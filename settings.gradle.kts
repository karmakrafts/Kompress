import java.time.Duration

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

rootProject.name = "kompress"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        gradlePluginPortal()
        maven("https://central.sonatype.com/repository/maven-snapshots")
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven("https://central.sonatype.com/repository/maven-snapshots")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver") version "1.0.0"
    id("com.gradleup.nmcp.settings") version "1.6.1"
}

nmcpSettings {
    providers.environmentVariable("OSSRH_USERNAME").orNull?.let { username ->
        centralPortal {
            this.username = username
            password = providers.environmentVariable("OSSRH_PASSWORD").get()
            validationTimeout = Duration.ofMinutes(30)
        }
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
include("kompress-core")
include("kompress-zlib")
include("kompress-gzip")
include("kompress-zip")
include("kompress-benchmarks")