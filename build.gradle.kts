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

import dev.karmakrafts.conventions.GitLabCI
import dev.karmakrafts.conventions.apache2License
import dev.karmakrafts.conventions.defaultDependencyLocking
import dev.karmakrafts.conventions.setRepository
import dev.karmakrafts.conventions.signPublications
import kotlin.io.encoding.ExperimentalEncodingApi

plugins {
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlinx.benchmark) apply false
    alias(libs.plugins.karmaConventions)
    signing
    `maven-publish`
}

group = "dev.karmakrafts.kompress"
version = GitLabCI.getDefaultVersion(libs.versions.kompress)

@OptIn(ExperimentalEncodingApi::class) subprojects {
    group = rootProject.group
    version = rootProject.version
    if (GitLabCI.isCI) defaultDependencyLocking()

    if ("benchmarks" in project.name) return@subprojects

    apply {
        plugin<MavenPublishPlugin>()
        plugin<SigningPlugin>()
    }

    publishing {
        apache2License()
        setRepository("github.com", "karmakrafts/Kompress")
        with(GitLabCI) { karmaKraftsDefaults() }
    }

    signing {
        signPublications()
    }
}