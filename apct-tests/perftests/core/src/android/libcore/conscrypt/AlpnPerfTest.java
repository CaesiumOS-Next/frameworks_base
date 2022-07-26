///*
// * Copyright 2017 The Android Open Source Project
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package android.libcore.conscrypt;
//
//import javax.net.ssl.SSLException;
//import com.android.org.conscrypt.EngineHandshakeBenchmark.Config;
//
//import org.junit.Before;
//import org.junit.Rule;
//import org.junit.Test;
//import org.junit.runner.RunWith;
//import org.junit.runners.Parameterized;
//
///**
// * Benchmark comparing ALPN performance between Conscrypt and Netty.
// */
//public class AlpnBenchmark {
//    private final JmhConfig config = new JmhConfig();
//
//    @Parameterized.Parameter(0)
//    public String a_cipher;
//
//    @Parameterized.Parameter(1)
//    public BufferType b_buffer;
//
//    // JDK does not support ALPN, so exclude it from the benchmarks.
//    @Parameterized.Parameter(2)
//    public OpenJdkEngineFactory c_engine;
//
//    private EngineHandshakeBenchmark benchmark;
//
//    @Before
//    public void setup() throws Exception {
//        benchmark = new EngineHandshakeBenchmark(config);
//    }
//
//    @Test
//    public void hs() throws SSLException {
//        benchmark.handshake();
//    }
//
//    private final class JmhConfig implements Config {
//
//        @Override
//        public BufferType bufferType() {
//            return b_buffer;
//        }
//
//        @Override
//        public EngineFactory engineFactory() {
//            return c_engine;
//        }
//
//        @Override
//        public String cipher() {
//            return a_cipher;
//        }
//
//        @Override
//        public boolean useAlpn() {
//            return true;
//        }
//
//        @Override
//        public int rttMillis() {
//            return 0;
//        }
//
//        @Override
//        public BenchmarkProtocol protocol() {
//            return BenchmarkProtocol.TLSv12;
//        }
//    }
//}
