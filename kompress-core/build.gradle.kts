/*
 * Copyright (C) 2025 Karma Krafts & associates
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
import dev.karmakrafts.conventions.setProjectInfo
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import java.time.Duration
import java.time.ZonedDateTime

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.jsPlainObjects)
    alias(libs.plugins.android.library)
    alias(libs.plugins.dokka)
    signing
    `maven-publish`
}

configureJava(libs.versions.java)

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
    androidTarget {
        publishLibraryVariants("debug", "release")
    }
    androidNativeArm32()
    androidNativeArm64()
    androidNativeX64()
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
        useEsModules()
        browser {
            testTask {
                useKarma {
                    timeout = Duration.ofSeconds(30)
                    useChromeHeadless()
                }
            }
        }
    }
    wasmJs {
        useEsModules()
        browser {
            testTask {
                useKarma {
                    timeout = Duration.ofSeconds(30)
                    useChromeHeadless()
                }
            }
        }
    }
    applyDefaultHierarchyTemplate {
        common {
            group("jvmAndAndroid") {
                withJvm()
                withAndroidTarget()
            }
        }
    }
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.io.bytestring)
                implementation(libs.kotlinx.io.core)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        webMain {
            dependencies {
                implementation(libs.kotlinx.browser)
                implementation(npm("fflate", libs.versions.fflate.get()))
            }
        }
    }
}

android {
    namespace = "$group.${rootProject.name}"
    compileSdk = libs.versions.androidCompileSDK.get().toInt()
    defaultConfig {
        minSdk = libs.versions.androidMinimalSDK.get().toInt()
    }
}

dokka {
    moduleName = project.name
    pluginsConfiguration {
        html {
            footerMessage = "(c) ${ZonedDateTime.now().year} Karma Krafts & associates"
        }
    }
}

val dokkaJar by tasks.registering(Jar::class) {
    group = "dokka"
    from(tasks.dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

tasks {
    withType<KotlinJvmTest>().configureEach {
        jvmArgs("-Xms2G", "-Xmx2G")
    }
    System.getProperty("publishDocs.root")?.let { docsDir ->
        register("publishDocs", Copy::class) {
            dependsOn(dokkaJar)
            mustRunAfter(dokkaJar)
            from(zipTree(dokkaJar.get().outputs.files.first()))
            into(docsDir)
        }
    }
}

publishing {
    setProjectInfo(
        name = "Kompress Core",
        description = "Lightweight zlib API for Kotlin Multiplatform",
        url = "https://git.karmakrafts.dev/kk/kompress"
    )
    publications.withType<MavenPublication>().configureEach {
        artifact(dokkaJar)
    }
}