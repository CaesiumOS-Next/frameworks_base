#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include <string>
#include <memory>

#include <androidfw/ApkAssets.h>
#include <androidfw/LoadedArsc.h>
#include <androidfw/StringPiece.h>

#include <fuzzer/FuzzedDataProvider.h>

using android::ApkAssets;
using android::LoadedArsc;
using android::StringPiece;

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {

  std::unique_ptr<const LoadedArsc> loaded_arsc =
  LoadedArsc::Load(StringPiece(reinterpret_cast<const char*>(data), size));

  return 0;
}