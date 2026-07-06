## [Unreleased]

## [2.3.1]

### Fixed

- Deflater not selecting the optimal block size when writing data, causing overallocation of the output in some cases ([GH-5](https://github.com/karmakrafts/Kompress/issues/5))

### Changed

- Updated to Gradle 9.6.1
- Updated to Karma Conventions 1.18.2
- Updated to Karbide 1.10.5
- Updated to Kotlin Wrappers 2026.7.0
- Updated to kotlinx.io 0.9.1
- Major performance improvement (~2,5x) of CRC32 on JVM
- Minor performance improvement for `Inflater` on all platforms

## [2.3.0]

### Added

- ZLIB support via new `ZlibCompressor` and `ZlibDecompressor` provided by `kompress-zlib`
- `zlibSink` and `zlibSource` extensions for `ZlibCompressor`
- `unzlibSink` and `unzlibSource` extensions for `ZlibDecompressor`
- `FramingCompressor` delegate compressor to support wrapping existing compressors with extra data
- `FramingDecompressor` delegate decompressor to support unwrapping extra data with existing decompressors
- Documentation for `kompress-gzip` APIs
- Documentation for `kompress-zip` APIs
- Documentation for `kompress-zlib` APIs

### Changed

- Updated NMCP to 1.6.1
- Major increase in test coverage for all modules

## [2.2.0]

### Added

- ZIP unarchiving support

### Changed

- Performance improvements on Kotlin/JS
- Minor performance improvements for decompression on all platforms
- Updated to Gradle 9.6.0
- Updated to Karma Conventions 1.18.1
- Updated to Karbide 1.10.3
- Updated to Kotlin Wrappers 2026.6.9
- Updated to OSHI 7.3.2
- Migrated to NMCP based Maven Central publishing

## [2.1.0]

### Added

- WASM WASI support
- `Platform.WASI` since WASI doesn't expose host platform

### Changed

- Updated to Karma Conventions 1.18.0
- Updated to Karbide 1.10.0

## [2.0.0]

### Added

- `Archiver<E, D>` interface for modeling streaming archivers
- `Unarchiver<E, D>` interface for modeling streaming unarchivers
- `Unarchiver<E, D>.extract` extension function
- `Compressor` interface for modeling streaming compressors
- `Decompressor` interface for modeling streaming decompressors
- `RawSource.compressingSource` and `RawSink.compressingSink` extension functions
- `RawSource.decompressingSource` and `RawSink.decompressingSink` extension functions
- `CRC32` for customizable and optimized checksum calculation
- `RawSink.crc32Sink` and `RawSource.crc32Source` extension functions
- `kompress-gzip` module for GZip archive support via `RawSink.gzip` and `Source.ungzip` extensions
- `kompress-zip` module for Zip archive support via `RawSink.zip` and `Source.unzip` extensions

### Changed

- Pure Kotlin implementation of `Deflater` and `Inflater`!
- Updated to Karma Conventions 1.17.1
- Downgraded to Gradle 9.4.1 because of IDEA compatibility regression
- Updated to Kotlin Wrappers 2026.6.3
- Updated to Android Gradle 9.2.1
- Deprecated `Deflater.deflate` bulk compression function in favor of `Deflater.compress`/`Deflater.compressBulk`
- Deprecated `Inflater.inflate` bulk decompression function in favor of `Inflater.decompress`/`Inflater.decompressBulk`
- Deprecated `Deflater.input` property setter in favor of `Compressor.setInput`
- Deprecated `Inflater.input` property setter in favor of `Decompressor.setInput`
- Library now depends on `dev.karmakrafts.karbide`

## [1.4.3]

## [1.4.2]

### Changed

- Updated to Kotlin 2.3.21
- Updated to Gradle 9.5.0
- Updated to Karma Conventions 1.16.1
- Updated to Android Gradle 9.2.0
- Updated to Kotlin Wrappers 2026.4.15

### Fixed

- Fixed build artifacts using experimental Kotlin features, preventing downstream use without them enabled

## [1.4.1]

### Added

- Added automatic changelog

### Changed

- Updated to Kotlin 2.3.20
- Updated to Gradle 9.4.1
- Updated to Karma Conventions 1.15.1
- Updated to Android Gradle 9.1.0
