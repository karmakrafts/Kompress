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

package dev.karmakrafts.kompress.util

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform as SystemPlatform

@OptIn(ExperimentalNativeApi::class)
internal actual val currentPlatform: Platform by lazy {
    when (SystemPlatform.osFamily) {
        OsFamily.WINDOWS -> Platform.WINDOWS
        OsFamily.MACOSX -> Platform.MACOS
        OsFamily.IOS -> Platform.IOS
        OsFamily.WATCHOS -> Platform.WATCHOS
        OsFamily.TVOS -> Platform.TVOS
        OsFamily.ANDROID -> Platform.ANDROID
        else -> Platform.LINUX
    }
}