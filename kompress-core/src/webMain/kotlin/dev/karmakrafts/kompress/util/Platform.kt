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

@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.karmakrafts.kompress.util

import dev.karmakrafts.kompress.InternalKompressApi
import web.navigator.navigator
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

private external interface Process {
    val platform: String
}

private external val process: Process

private fun checkIsNode(): Boolean = js("""typeof process !== 'undefined' && process.release.name === 'node'""")

internal val isNode: Boolean by lazy(::checkIsNode)

@OptIn(InternalKompressApi::class)
private fun getPlatformForName(name: String): Platform {
    return when { // @formatter:off
        name.contains("win32", ignoreCase = true)
            || name.contains("windows", ignoreCase = true)
            || name.contains("winnt", ignoreCase = true) -> Platform.WINDOWS
        name.contains("macos", ignoreCase = true)
            || name.contains("osx", ignoreCase = true) -> Platform.MACOS
        name.contains("ios", ignoreCase = true)
            || (name.contains("macintel", ignoreCase = true) && navigator.maxTouchPoints > 1) -> Platform.IOS
        name.contains("tvos", ignoreCase = true) -> Platform.TVOS
        name.contains("watchos", ignoreCase = true) -> Platform.WATCHOS
        name.contains("android", ignoreCase = true) -> Platform.ANDROID
        else -> Platform.LINUX
    }
}

@OptIn(InternalKompressApi::class)
private fun getNodePlatform(): Platform = getPlatformForName(process.platform)

@OptIn(InternalKompressApi::class)
private fun getBrowserPlatform(): Platform = getPlatformForName(navigator.platform)

@InternalKompressApi
actual val currentPlatform: Platform by lazy {
    if(isNode) getNodePlatform()
    else getBrowserPlatform()
}