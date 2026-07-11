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

@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import dev.karmakrafts.conventions.configureJava
import dev.karmakrafts.conventions.kotlin.defaultCompilerOptions
import dev.karmakrafts.conventions.kotlin.withBrowser
import dev.karmakrafts.conventions.kotlin.withJvm
import dev.karmakrafts.conventions.kotlin.withNative
import dev.karmakrafts.conventions.kotlin.withNodeJs
import dev.karmakrafts.conventions.kotlin.withWasmWasi
import dev.karmakrafts.conventions.kotlin.withWeb
import kotlinx.benchmark.gradle.BenchmarkConfiguration
import kotlinx.benchmark.gradle.benchmark
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlinx.benchmark)
}

configureJava(libs.versions.javaCompile, libs.versions.javaTarget)

kotlin {
    defaultCompilerOptions()
    withNative {
        binaries {
            test(listOf(NativeBuildType.RELEASE))
        }
    }
    withJvm()
    withWeb {
        withBrowser {
            useEsModules()
        }
        withNodeJs()
    }
    withWasmWasi {
        withNodeJs()
    }
    applyDefaultHierarchyTemplate()
    sourceSets {
        commonMain {
            dependencies {
                api(projects.kompressCore)
                implementation(libs.karbide.core) // Provided by core
                implementation(libs.kotlinx.benchmark.runtime)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.jmh.core)
            }
        }
    }
}

benchmark {
    targets {
        register("jvm")
        register("linuxX64")
        register("linuxArm64")
        register("mingwX64")
        register("js")
        register("wasmJs")
        register("wasmWasi")
    }
    configurations {
        fun BenchmarkConfiguration.defaultConfig() {
            warmups = 10
            iterations = 10
            iterationTime = 1
            iterationTimeUnit = "s"
        }
        named("main") {
            defaultConfig()
        }
        register("crc") {
            include("dev.karmakrafts.kompress.*CRC32*")
            defaultConfig()
        }
        register("deflate") {
            include("dev.karmakrafts.kompress.*Deflater*")
            defaultConfig()
        }
        register("deflateDefaultLevel") {
            include("dev.karmakrafts.kompress.*DeflaterDefaultLevel*")
            defaultConfig()
        }
        register("inflate") {
            include("dev.karmakrafts.kompress.*Inflater*")
            defaultConfig()
        }
        register("inflateDefaultLevel") {
            include("dev.karmakrafts.kompress.*InflaterDefaultLevel*")
            defaultConfig()
        }
        register("deflateInflate") {
            include("dev.karmakrafts.kompress.*Inflater*")
            include("dev.karmakrafts.kompress.*Deflater*")
            defaultConfig()
        }
    }
}

tasks {
    // For all JVM benchmark tasks, we infer module path and add the correct module
    named { name -> "jvm" in name && "Benchmark" in name }.withType<JavaExec>().configureEach {
        modularity.inferModulePath = true
        jvmArgs("--add-modules", "jdk.incubator.vector")
    }
}