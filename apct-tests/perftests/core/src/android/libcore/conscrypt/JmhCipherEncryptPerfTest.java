/*
 * Copyright 2017 The Android Open Source Project
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

package android.libcore.conscrypt;

import android.perftests.utils.BenchmarkState;
import android.perftests.utils.PerfStatusReporter;
import android.test.suitebuilder.annotation.LargeTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collection;

/**
 * Benchmark comparing Cipher creation performance.
 */
@RunWith(Parameterized.class)
@LargeTest
public class JmhCipherEncryptPerfTest {
    private final JmhConfig config = new JmhConfig();

    @Parameterized.Parameter(0)
    public Transformation a_tx;

    @Parameterized.Parameter(1)
    public CipherEncryptPerfTest.BufferType b_bufferType;

    @Parameterized.Parameter(2)
    public OpenJdkCipherFactory c_provider;

    @Parameterized.Parameters(name = "a_tx({0}), b_bufferType({1}), c_provider({2})")
    public static Collection<Object[]> data() {
        final List<Object[]> params = new ArrayList<>();
        for (Transformation transformation :  Transformation.values()) {
            for (CipherEncryptPerfTest.BufferType bufferType :  CipherEncryptPerfTest.BufferType.values()) {
                for (OpenJdkCipherFactory factory : OpenJdkCipherFactory.values()) {
                    params.add(
                            new Object[] {
                                    transformation, bufferType, factory
                            });

                }
            }
        }
    }


    private CipherEncryptPerfTest benchmark;

    @Before
    public void setup() throws Exception {
        benchmark = new CipherEncryptPerfTest(config);
    }

    @Test
    public void encrypt() throws Exception {
        benchmark.encrypt();
    }

    private final class JmhConfig implements CipherEncryptPerfTest.Config {
        @Override
        public CipherEncryptPerfTest.BufferType bufferType() {
            return b_bufferType;
        }

        @Override
        public CipherFactory cipherFactory() {
            return c_provider;
        }

        @Override
        public Transformation transformation() {
            return a_tx;
        }
    }
}