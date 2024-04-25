/*
 * Copyright 2020 The Android Open Source Project
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

#include "ImageDecoderFuzzerBase.h"

#ifdef PNG_MUTATOR_DEFINE_LIBFUZZER_CUSTOM_MUTATOR
#include <fuzz/png_mutator.h>
#endif

#define FOURCC(c1, c2, c3, c4) ((c1) << 24 | (c2) << 16 | (c3) << 8 | (c4))

/**
 * Reverse all 4 bytes in a 32bit value.
 * e.g. 0x12345678 -> 0x78563412
 */
static uint32_t endianSwap32(uint32_t value) {
    return ((value & 0xFF) << 24) | ((value & 0xFF00) << 8) | ((value & 0xFF0000) >> 8) |
            (value >> 24);
}

static bool isFtyp(const uint8_t* data, size_t size) {
    constexpr int32_t headerSize = 8;
    constexpr int32_t chunkTypeOffset = 4;
    constexpr int32_t ftypFourCCVal = FOURCC('f', 't', 'y', 'p');
    if (size >= headerSize) {
        const uint32_t* chunk = reinterpret_cast<const uint32_t*>(data + chunkTypeOffset);
        if (endianSwap32(*chunk) == ftypFourCCVal) {
            return true;
        }
    }
    return false;
}

class imageDecoderFuzzer : public imgFuzzerHelper {
public:
    imageDecoderFuzzer(const uint8_t* data, size_t size) : imgFuzzerHelper(data, size) {}
    bool initData();
};

bool imageDecoderFuzzer::initData() {
    bool result;
    /**
     * Use maximum of 80% of buffer for creating decoder and save atleast 20% for fuzzing other APIs
     */
    int32_t maxDataSize = (mDataProvider.remaining_bytes() * 80) / 100;
    int32_t dataSize = mDataProvider.ConsumeIntegralInRange<int32_t>(0, maxDataSize);
    std::vector<uint8_t> inputBuffer = mDataProvider.ConsumeBytes<uint8_t>(dataSize);
    if (isFtyp(inputBuffer.data(), inputBuffer.size())) {
        /* We want to ignore HEIF data when fuzzing non-HEIF image decoders. Use 'FTYP'
         * as a signal to ignore, though note that this excludes more than just HEIF.
         * But when this code was added, `AImageDecoder` did not support any formats
         * in 'FTYP' besides HEIF.
         */
        return false;
    }
    result = init(inputBuffer.data(), inputBuffer.size());
    return result;
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    imageDecoderFuzzer fuzz(data, size);
    if (!fuzz.initData()) {
        return 0;
    }
    fuzz.process();
    return 0;
}
