/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.platform.coverage

import com.android.tools.metalava.model.text.ApiFile
import java.io.File
import java.io.FileWriter

/** Usage: extract-flagged-apis <api text file> <output .pb file> */
fun main(args: Array<String>) {
    var cb = ApiFile.parseApi(listOf(File(args[0])))
    var builder = FlagApiMap.newBuilder()
    for (pkg in cb.getPackages().packages) {
        var packageName = pkg.qualifiedName()
        pkg.allClasses()
            .filter { it.methods().size > 0 }
            .forEach {
                val classFlag =
                    it.modifiers
                        .findAnnotation("android.annotation.FlaggedApi")
                        ?.findAttribute("value")
                        ?.value
                        ?.value()
                // If a class is annotated by @FlaggedApi, all of its methods and constructors are
                // flagged.
                if (classFlag != null && classFlag is String) {
                    for (ctor in it.constructors()) {
                        var api =
                            JavaMethod.newBuilder()
                                .setPackageName(packageName)
                                .setClassName(it.fullName())
                                .setMethodName(ctor.name())
                        for (param in ctor.parameters()) {
                            api.addParameters(param.type().toTypeString())
                        }
                        addFlaggedApi(builder, api, classFlag)
                    }
                    for (method in it.methods()) {
                        var api =
                            JavaMethod.newBuilder()
                                .setPackageName(packageName)
                                .setClassName(it.fullName())
                                .setMethodName(method.name())
                        for (param in method.parameters()) {
                            api.addParameters(param.type().toTypeString())
                        }
                        addFlaggedApi(builder, api, classFlag)
                    }
                } else {
                    // If a class is not annotated by @FlaggedApi, find methods and constructors
                    // annotated by @FlaggedApi.
                    for (ctor in it.constructors()) {
                        val methodFlag =
                            ctor.modifiers
                                .findAnnotation("android.annotation.FlaggedApi")
                                ?.findAttribute("value")
                                ?.value
                                ?.value()
                        if (methodFlag != null && methodFlag is String) {
                            var api =
                                JavaMethod.newBuilder()
                                    .setPackageName(packageName)
                                    .setClassName(it.fullName())
                                    .setMethodName(ctor.name())
                            for (param in ctor.parameters()) {
                                api.addParameters(param.type().toTypeString())
                            }
                            addFlaggedApi(builder, api, methodFlag)
                        }
                    }
                    for (method in it.methods()) {
                        val methodValue =
                            method.modifiers
                                .findAnnotation("android.annotation.FlaggedApi")
                                ?.findAttribute("value")
                                ?.value
                                ?.value()
                        if (methodValue != null && methodValue is String) {
                            var api =
                                JavaMethod.newBuilder()
                                    .setPackageName(packageName)
                                    .setClassName(it.fullName())
                                    .setMethodName(method.name())
                            for (param in method.parameters()) {
                                api.addParameters(param.type().toTypeString())
                            }
                            addFlaggedApi(builder, api, methodValue)
                        }
                    }
                }
            }
    }
    val flagApiMap = builder.build()
    FileWriter(args[1]).use { it.write(flagApiMap.toString()) }
}

fun addFlaggedApi(builder: FlagApiMap.Builder, api: JavaMethod.Builder, flag: String) {
    if (builder.containsFlagToApi(flag)) {
        var updatedApis = builder.getFlagToApiOrThrow(flag).toBuilder().addJavaMethods(api).build()
        builder.putFlagToApi(flag, updatedApis)
    } else {
        var apis = FlaggedApis.newBuilder().addJavaMethods(api).build()
        builder.putFlagToApi(flag, apis)
    }
}
