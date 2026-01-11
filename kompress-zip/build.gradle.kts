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

import com.android.build.api.dsl.KotlinMultiplatformAndroidCompilation
import dev.karmakrafts.conventions.configureJava
import dev.karmakrafts.conventions.defaultDokkaConfig
import dev.karmakrafts.conventions.setProjectInfo
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
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
defaultDokkaConfig()

@OptIn(ExperimentalWasmDsl::class) kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    withSourcesJar(true)
    mingwX64()
    linuxX64()
    linuxArm64()
    macosX64()
    macosArm64()
    androidLibrary {
        namespace = "$group.${rootProject.name}"
        compileSdk = libs.versions.androidCompileSDK.get().toInt()
        minSdk = libs.versions.androidMinimalSDK.get().toInt()
    }
    androidNativeArm32()
    androidNativeArm64()
    androidNativeX64()
    androidNativeX86()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    tvosArm64()
    tvosX64()
    tvosSimulatorArm64()
    watchosArm32()
    watchosArm64()
    watchosX64()
    watchosSimulatorArm64()
    jvm()
    js {
        useCommonJs()
        browser {
            testTask {
                useKarma {
                    timeout = Duration.ofSeconds(30)
                    useChromeHeadless()
                }
            }
        }
        nodejs {
            testTask {
                useKarma {
                    timeout = Duration.ofSeconds(30)
                    useChromeHeadless()
                }
            }
        }
    }
    wasmJs {
        browser {
            testTask {
                useKarma {
                    timeout = Duration.ofSeconds(30)
                    useChromeHeadless()
                }
            }
        }
        nodejs {
            testTask {
                useKarma {
                    timeout = Duration.ofSeconds(30)
                    useChromeHeadless()
                }
            }
        }
    }
    applyDefaultHierarchyTemplate()
    sourceSets {
        commonMain {
            dependencies {

            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
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
        name = "Kompress ZIP",
        description = "PKZip implementation for Kotlin Multiplatform",
        url = "https://git.karmakrafts.dev/kk/kompress"
    )
}