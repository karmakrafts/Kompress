# 🗜️ Kompress

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