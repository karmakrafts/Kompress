/*
 * Copyright 2026 Karma Krafts
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("Platform$")

package dev.karmakrafts.kompress.util

import dev.karmakrafts.kompress.InternalCompressionApi
import jdk.incubator.vector.IntVector
import oshi.util.PlatformEnum

internal val hasVectorSupport: Boolean by lazy {
    ModuleLayer.boot().findModule("jdk.incubator.vector").isPresent
}

internal val has256BitSimd: Boolean by lazy {
    if (!hasVectorSupport) return@lazy false
    IntVector.SPECIES_PREFERRED.vectorBitSize() >= 256
}

@InternalCompressionApi
actual val currentPlatform: Platform by lazy {
    when (PlatformEnum.getCurrentPlatform()) {
        PlatformEnum.WINDOWS, PlatformEnum.WINDOWSCE -> Platform.WINDOWS
        PlatformEnum.MACOS -> Platform.MACOS
        else -> Platform.LINUX
    }
}