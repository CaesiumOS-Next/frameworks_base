/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.networkstack.util;

import static android.net.DnsResolver.TYPE_A;
import static android.net.DnsResolver.TYPE_AAAA;
import static android.net.util.NetworkStackUtils.NAMESPACE_CONNECTIVITY;

import android.annotation.NonNull;
import android.net.DnsResolver;
import android.net.Network;
import android.net.TrafficStats;
import android.net.util.NetworkStackUtils;
import android.util.Log;

import com.android.internal.util.TrafficStatsConstants;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Collection of utilities for dns query.
 */
public class DnsUtils {
    private static final String TAG = DnsUtils.class.getSimpleName();
    private static final String CONFIG_PRIVATE_DNS_RESOLUTION_TIMEOUT =
            "private_dns_resolution_timeout";
    // Enough for 3 DNS queries 5 seconds apart.
    private static final int DEFAULT_PRIVATE_DNS_RESOLUTION_TIMEOUT = 12500;


    /**
     * Return both A and AAAA query results regardless the ip address type of the giving network.
     */
    @NonNull
    public static InetAddress[] getAllTypesAddressesByName(@NonNull final Network network,
            @NonNull String host, int timeout) throws UnknownHostException {
        final List<InetAddress> result = new ArrayList<InetAddress>();

        result.addAll(Arrays.asList(
                getAddressByNameWithType(network, host, TYPE_A, DnsResolver.FLAG_NO_CACHE_LOOKUP,
                timeout)));
        result.addAll(Arrays.asList(
                getAddressByNameWithType(network, host, TYPE_AAAA, DnsResolver.FLAG_NO_CACHE_LOOKUP,
                timeout)));
        return result.toArray(new InetAddress[0]);
    }

    /**
     * Return dns query result based on the QueryType(TYPE_A or TYPE_AAAA) taken.
     */
    @NonNull
    public static InetAddress[] getAddressByNameWithType(@NonNull final Network network,
            @NonNull final String host, int type, int flag, int timeoutMs)
            throws UnknownHostException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<List<InetAddress>> resultRef = new AtomicReference<>();

        final DnsResolver.Callback<List<InetAddress>> callback =
                new DnsResolver.Callback<List<InetAddress>>() {
            @Override
            public void onAnswer(List<InetAddress> answer, int rcode) {
                if (rcode == 0) {
                    resultRef.set(answer);
                }
                latch.countDown();
            }

            @Override
            public void onError(@NonNull DnsResolver.DnsException e) {
                Log.d(TAG, "DNS error resolving " + host + ": " + e.getMessage());
                latch.countDown();
            }
        };
        final int oldTag = TrafficStats.getAndSetThreadStatsTag(
                TrafficStatsConstants.TAG_SYSTEM_PROBE);
        DnsResolver.getInstance().query(network, host, type, flag, r -> r.run(),
                null /* cancellationSignal */, callback);
        TrafficStats.setThreadStatsTag(oldTag);

        try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        }


        final List<InetAddress> result = resultRef.get();
        if (result == null || result.size() == 0) {
            throw new UnknownHostException(host);
        }

        return result.toArray(new InetAddress[0]);
    }

    public static int getPrivateDnsResolutionTimeout() {
        return NetworkStackUtils.getDeviceConfigPropertyInt(NAMESPACE_CONNECTIVITY,
                CONFIG_PRIVATE_DNS_RESOLUTION_TIMEOUT, DEFAULT_PRIVATE_DNS_RESOLUTION_TIMEOUT);
    }

}
