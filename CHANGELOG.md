## [Unreleased]

### Added

- Support for GZIP via `GZipCompressor`/`GZipDecompressor`
- Support for ZIP via `ZipCompressor/ZipDecompressor`
- Added `Compressor` interface
- Added `Decompressor` interface
- Added `RawSource.compressing()` extension function
- Added `RawSource.decompressing()` extension function
- Added `Source.crc32` extension function
- Added `crc32(ByteArray)` function

### Changed

- Updated to Kotlin Wrappers 2026.5.3
- Updated to Android Gradle 9.2.1
- Deprecated `Deflater.deflate` bulk compression function in favor of `Deflater.compress`
- Deprecated `Inflater.inflate` bulk decompression function in favor of `Inflater.decompress`

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
