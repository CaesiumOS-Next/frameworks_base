/*
 * Copyright 2024 The Android Open Source Project
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

#include <fakeservicemanager/FakeServiceManager.h>

#include "ImageDecoderFuzzerBase.h"
#ifdef __ANDROID__
#include <MediaExtractorService.h>
#include <MediaPlayerService.h>
#else
#include <fuzzbinder/random_binder.h>
#endif //__ANDROID__
using namespace android;
std::once_flag callOnceHEIF;

class heifImageDecoderFuzzer : public imgFuzzerHelper {
public:
    heifImageDecoderFuzzer(const uint8_t* data, size_t size) : imgFuzzerHelper(data, size) {}
    bool initData();
};

bool heifImageDecoderFuzzer::initData() {
#ifndef __ANDROID__
    std::call_once(callOnceHEIF, [&] {
        sp<IServiceManager> fakeServiceManager = new FakeServiceManager();
        setDefaultServiceManager(fakeServiceManager);
        auto binderExtractor = getRandomBinder(&mDataProvider);
        auto binderPlayer = getRandomBinder(&mDataProvider);
        fakeServiceManager->addService(String16("media.extractor"), binderExtractor);
        fakeServiceManager->addService(String16("media.player"), binderPlayer);
    });
#endif //__ANDROID__
    bool result;
    /**
     * Use maximum of 80% of buffer for creating decoder and save atleast 20% for fuzzing other APIs
     */
    int32_t maxDataSize = (mDataProvider.remaining_bytes() * 80) / 100;
    int32_t dataSize = mDataProvider.ConsumeIntegralInRange<int32_t>(0, maxDataSize);
    std::vector<uint8_t> inputBuffer = mDataProvider.ConsumeBytes<uint8_t>(dataSize);
    result = init(inputBuffer.data(), inputBuffer.size());
    return result;
}

#ifdef __ANDROID__
extern "C" int LLVMFuzzerInitialize(int* /* argc */, char*** /* argv */) {
    /**
     * For image formats like HEIF, a new metadata object is
     * created which requires "media.player" service running
     */
    sp<IServiceManager> fakeServiceManager = new FakeServiceManager();
    setDefaultServiceManager(fakeServiceManager);
    MediaPlayerService::instantiate();
    MediaExtractorService::instantiate();
    return 0;
}
#endif //__ANDROID__

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    heifImageDecoderFuzzer fuzz(data, size);
    if (!fuzz.initData()) {
        return 0;
    }
    fuzz.process();
    return 0;
}
