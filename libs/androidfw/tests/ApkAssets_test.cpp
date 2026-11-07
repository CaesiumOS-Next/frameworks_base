/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "androidfw/ApkAssets.h"

#include <cstdlib>
#include <cstdint>
#include <string>
#include <unistd.h>
#include <vector>

#include "android-base/file.h"
#include "android-base/test_utils.h"
#include "android-base/unique_fd.h"
#include "androidfw/Util.h"

#include "TestHelpers.h"
#include "data/basic/R.h"

using ::android::base::unique_fd;
using ::com::android::basic::R;
using ::testing::Eq;
using ::testing::Ge;
using ::testing::IsNull;
using ::testing::NotNull;
using ::testing::SizeIs;
using ::testing::StrEq;

namespace android {

TEST(ApkAssetsTest, LoadApk) {
  auto loaded_apk = ApkAssets::Load(GetTestDataPath() + "/basic/basic.apk");
  ASSERT_THAT(loaded_apk, NotNull());

  const LoadedArsc* loaded_arsc = loaded_apk->GetLoadedArsc();
  ASSERT_THAT(loaded_arsc, NotNull());
  ASSERT_THAT(loaded_arsc->GetPackageById(0x7fu), NotNull());
  ASSERT_THAT(loaded_apk->GetAssetsProvider()->Open("res/layout/main.xml"), NotNull());
}

TEST(ApkAssetsTest, RejectsUnsupportedCompressionMethod) {
  constexpr uint16_t kUnsupportedCompressionMethod = 12;
  constexpr uint32_t kCompressedSize = 32;
  constexpr uint32_t kDeclaredUncompressedSize = 16 * 1024 * 1024;
  const std::string entry_name = "res/xml/print_service.xml";
  std::vector<uint8_t> zip;

  const auto append_u16 = [&zip](uint16_t value) {
    zip.push_back(static_cast<uint8_t>(value));
    zip.push_back(static_cast<uint8_t>(value >> 8));
  };
  const auto append_u32 = [&zip](uint32_t value) {
    for (size_t shift = 0; shift < 32; shift += 8) {
      zip.push_back(static_cast<uint8_t>(value >> shift));
    }
  };
  const auto append_name = [&zip, &entry_name]() {
    zip.insert(zip.end(), entry_name.begin(), entry_name.end());
  };

  append_u32(0x04034b50);
  append_u16(20);
  append_u16(0);
  append_u16(kUnsupportedCompressionMethod);
  append_u16(0);
  append_u16(0);
  append_u32(0);
  append_u32(kCompressedSize);
  append_u32(kDeclaredUncompressedSize);
  append_u16(static_cast<uint16_t>(entry_name.size()));
  append_u16(0);
  append_name();
  zip.insert(zip.end(), kCompressedSize, uint8_t{0});

  const uint32_t central_directory_offset = static_cast<uint32_t>(zip.size());
  append_u32(0x02014b50);
  append_u16(20);
  append_u16(20);
  append_u16(0);
  append_u16(kUnsupportedCompressionMethod);
  append_u16(0);
  append_u16(0);
  append_u32(0);
  append_u32(kCompressedSize);
  append_u32(kDeclaredUncompressedSize);
  append_u16(static_cast<uint16_t>(entry_name.size()));
  append_u16(0);
  append_u16(0);
  append_u16(0);
  append_u16(0);
  append_u32(0);
  append_u32(0);
  append_name();

  const uint32_t central_directory_size =
      static_cast<uint32_t>(zip.size()) - central_directory_offset;
  append_u32(0x06054b50);
  append_u16(0);
  append_u16(0);
  append_u16(1);
  append_u16(1);
  append_u32(central_directory_size);
  append_u32(central_directory_offset);
  append_u16(0);

  TemporaryFile file;
  ASSERT_TRUE(base::WriteFully(file.fd, zip.data(), zip.size()));
  auto provider = ZipAssetsProvider::Create(file.path, 0);
  ASSERT_THAT(provider, NotNull());
  EXPECT_THAT(provider->Open(entry_name, Asset::ACCESS_STREAMING), IsNull());

  // Without the method allowlist in FindEntry/OpenInternal, the method-12 entry is treated like
  // STORED data and mapped using the attacker-controlled 16 MiB uncompressed length. Touching the
  // final byte of that mapping crosses the tiny ZIP's file-backed range and terminates the child
  // with SIGBUS. A patched parser rejects the entry before the mapping exists, so the child exits
  // cleanly instead.
  EXPECT_EXIT(
      {
        auto child_provider = ZipAssetsProvider::Create(file.path, 0);
        if (child_provider == nullptr) {
          _exit(EXIT_FAILURE);
        }
        auto asset = child_provider->Open(entry_name, Asset::ACCESS_BUFFER);
        if (asset == nullptr) {
          _exit(EXIT_SUCCESS);
        }

        const auto mapped_data = asset->getIncFsBuffer(false).convert<uint8_t>();
        const volatile uint8_t* bytes = mapped_data.unsafe_ptr();
        if (bytes == nullptr) {
          _exit(EXIT_FAILURE);
        }
        const volatile uint8_t last_byte = bytes[kDeclaredUncompressedSize - 1];
        static_cast<void>(last_byte);
        _exit(EXIT_FAILURE);
      },
      ::testing::ExitedWithCode(EXIT_SUCCESS), "");
}

TEST(ApkAssetsTest, LoadApkFromFd) {
  const std::string path = GetTestDataPath() + "/basic/basic.apk";
  unique_fd fd(::open(path.c_str(), O_RDONLY | O_BINARY));
  ASSERT_THAT(fd.get(), Ge(0));

  auto loaded_apk = ApkAssets::LoadFromFd(std::move(fd), path);
  ASSERT_THAT(loaded_apk, NotNull());

  const LoadedArsc* loaded_arsc = loaded_apk->GetLoadedArsc();
  ASSERT_THAT(loaded_arsc, NotNull());
  ASSERT_THAT(loaded_arsc->GetPackageById(0x7fu), NotNull());
  ASSERT_THAT(loaded_apk->GetAssetsProvider()->Open("res/layout/main.xml"), NotNull());
}

TEST(ApkAssetsTest, LoadApkAsSharedLibrary) {
  auto loaded_apk = ApkAssets::Load(GetTestDataPath() + "/appaslib/appaslib.apk");
  ASSERT_THAT(loaded_apk, NotNull());

  const LoadedArsc* loaded_arsc = loaded_apk->GetLoadedArsc();
  ASSERT_THAT(loaded_arsc, NotNull());
  ASSERT_THAT(loaded_arsc->GetPackages(), SizeIs(1u));
  EXPECT_FALSE(loaded_arsc->GetPackages()[0]->IsDynamic());

  loaded_apk = ApkAssets::Load(GetTestDataPath() + "/appaslib/appaslib.apk",
                               nullptr, PROPERTY_DYNAMIC);
  ASSERT_THAT(loaded_apk, NotNull());

  loaded_arsc = loaded_apk->GetLoadedArsc();
  ASSERT_THAT(loaded_arsc, NotNull());
  ASSERT_THAT(loaded_arsc->GetPackages(), SizeIs(1u));
  EXPECT_TRUE(loaded_arsc->GetPackages()[0]->IsDynamic());
}

TEST(ApkAssetsTest, CreateAndDestroyAssetKeepsApkAssetsOpen) {
  auto loaded_apk = ApkAssets::Load(GetTestDataPath() + "/basic/basic.apk");
  ASSERT_THAT(loaded_apk, NotNull());

  { ASSERT_THAT(loaded_apk->GetAssetsProvider()->Open("res/layout/main.xml",
                                                      Asset::ACCESS_BUFFER), NotNull()); }

  { ASSERT_THAT(loaded_apk->GetAssetsProvider()->Open("res/layout/main.xml",
                                                      Asset::ACCESS_BUFFER), NotNull()); }
}

TEST(ApkAssetsTest, OpenUncompressedAssetFd) {
  auto loaded_apk = ApkAssets::Load(GetTestDataPath() + "/basic/basic.apk");
  ASSERT_THAT(loaded_apk, NotNull());

  auto asset = loaded_apk->GetAssetsProvider()->Open("assets/uncompressed.txt",
                                                     Asset::ACCESS_UNKNOWN);
  ASSERT_THAT(asset, NotNull());

  off64_t start, length;
  unique_fd fd(asset->openFileDescriptor(&start, &length));
  ASSERT_THAT(fd.get(), Ge(0));

  lseek64(fd.get(), start, SEEK_SET);

  std::string buffer;
  buffer.resize(length);
  ASSERT_TRUE(base::ReadFully(fd.get(), &*buffer.begin(), length));

  EXPECT_THAT(buffer, StrEq("This should be uncompressed.\n\n"));
}

TEST(ApkAssetsTest, TakeAssetsProviderNotCrashing) {
  // Make sure the apk assets object can survive taking its assets provider and doesn't crash
  // the process.
  {
    auto loaded_apk = ApkAssets::Load(GetTestDataPath() + "/basic/basic.apk");
    ASSERT_THAT(loaded_apk, NotNull());

    auto provider = std::move(*loaded_apk).TakeAssetsProvider();
    ASSERT_THAT(provider, NotNull());
  }
  // If this test doesn't crash by this point we're all good.
}

TEST(ApkAssetsTest, AssetsProviderNotMovedOnError) {
  auto assets_provider
      = ZipAssetsProvider::Create(GetTestDataPath() + "/bad/bad.apk", 0);
  ASSERT_THAT(assets_provider, NotNull());
  auto loaded_apk = ApkAssets::Load(std::move(assets_provider));
  ASSERT_THAT(loaded_apk, IsNull());
  ASSERT_THAT(assets_provider, NotNull());
}

}  // namespace android
