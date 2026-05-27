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
import dev.karmakrafts.conventions.dokka.configureDokka
import dev.karmakrafts.conventions.kotlin.defaultCompilerOptions
import dev.karmakrafts.conventions.kotlin.withAndroidLibrary
import dev.karmakrafts.conventions.kotlin.withBrowser
import dev.karmakrafts.conventions.kotlin.withJvm
import dev.karmakrafts.conventions.kotlin.withNative
import dev.karmakrafts.conventions.kotlin.withNodeJs
import dev.karmakrafts.conventions.kotlin.withWeb
import dev.karmakrafts.conventions.setProjectInfo
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import java.time.Duration

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.dokka)
    signing
    `maven-publish`
}

configureJava(libs.versions.java)

configureDokka {
    withKotlin()
    withKotlinxIo()
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    defaultCompilerOptions()
    withSourcesJar()
    withAndroidLibrary("$group.core")
    withNative {
        binaries {
            test(listOf(NativeBuildType.RELEASE))
        }
    }
    withJvm()
    withWeb {
        withBrowser {
            useEsModules()
            testTask {
                timeout = Duration.ofSeconds(30)
                useKarma {
                    useConfigDirectory(rootProject.projectDir.resolve("karma.config.d"))
                }
            }
        }
        withNodeJs {
            testTask {
                timeout = Duration.ofSeconds(30)
                useMocha {
                    timeout = "30000"
                }
            }
        }
    }
    applyDefaultHierarchyTemplate {
        common {
            group("jvmAndAndroid") {
                withJvm()
                withAndroidLibrary()
            }
        }
    }
    sourceSets {
        commonMain {
            dependencies {
                api(libs.kotlinx.io.bytestring)
                api(libs.kotlinx.io.core)
                api(libs.kotlinx.datetime)
                implementation(libs.karbide.core)
            }
        }
        jvmMain {
            dependencies {
                implementation(libs.oshi.core)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
        webMain {
            dependencies {
                implementation(libs.kotlin.wrappers.browser)
                implementation(npm("fflate", libs.versions.fflate.get()))
            }
        }
    }
}

tasks {
    withType<KotlinJvmTest>().configureEach {
        jvmArgs("-Xms2G", "-Xmx2G")
    }
}

publishing {
    setProjectInfo(
        name = "Kompress Core",
        description = "Lightweight compression API for Kotlin Multiplatform",
        url = "https://git.karmakrafts.dev/kk/kompress"
    )
}