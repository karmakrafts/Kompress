# 🗜️ Kompress

[![](https://git.karmakrafts.dev/kk/kompress/badges/master/pipeline.svg)](https://git.karmakrafts.dev/kk/kompress/-/pipelines)
[![](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Frepo.maven.apache.org%2Fmaven2%2Fdev%2Fkarmakrafts%2Fkompress%2Fkompress-core%2Fmaven-metadata.xml
)](https://git.karmakrafts.dev/kk/kompress/-/packages)
[![](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fdev%2Fkarmakrafts%2Fkompress%2Fkompress-core%2Fmaven-metadata.xml
)](https://git.karmakrafts.dev/kk/kompress/-/packages)

Lightweight zlib (de)compression API for Kotlin Multiplatform.  

### Features

- Supports all Kotlin Multiplatform targets
- Support for **DEFLATE** and **DEFLATE RAW** compression
- Synchronous streaming API inspired by Java's `Inflater`/`Deflater` APIs
- Integration with [kotlinx.io](https://github.com/Kotlin/kotlinx-io)
- Customizable compression- and memory-level
- Extra lightweight on JVM and native because it wraps available platform APIs

### How to use it

First, add the official Maven Central repository to your settings.gradle.kts:

```kotlin
pluginManagement {
    repositories {
        maven("https://central.sonatype.com/repository/maven-snapshots")
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://central.sonatype.com/repository/maven-snapshots")
        mavenCentral()
    }
}
```

Then add a dependency on the library in your root buildscript:

```kotlin
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("dev.karmakrafts.kompress:kompress-core:<version>")
            }
        }
    }
}
```

Or, if you are only using Kotlin/JVM, add it to your top-level dependencies block instead.