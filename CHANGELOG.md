## [Unreleased]

### Added

- Added `Archiver<E, D>` interface for modeling streaming archivers
- Added `Unarchiver<E, D>` interface for modeling streaming unarchivers
- Added `Unarchiver<E, D>.extract` extension function
- Added `Compressor` interface for modeling streaming compressors
- Added `Decompressor` interface for modeling streaming decompressors
- Added `RawSource.compressingSource` and `RawSink.compressingSink` extension functions
- Added `RawSource.decompressingSource` and `RawSink.decompressingSink` extension functions
- Added `Source.crc32` extension function
- Added `RawSink.crc32Sink` and `RawSource.crc32Source` extension functions
- Added `crc32(ByteArray)` function
- Added `kompress-gzip` module for GZip archive support via `RawSink.gzip` and `Source.ungzip` extensions
- Added `kompress-lz4` module for LZ4 compression support via `LZ4Compressor` and `LZ4Decompressor`

### Changed

- Updated to Gradle 9.5.1
- Updated to Kotlin Wrappers 2026.5.4
- Updated to Android Gradle 9.2.1
- Deprecated `Deflater.deflate` bulk compression function in favor of `Deflater.compress`/`Deflater.compressBulk`
- Deprecated `Inflater.inflate` bulk decompression function in favor of `Inflater.decompress`/`Inflater.decompressBulk`
- Deprecated `Deflater.input` property setter in favor of `Compressor.setInput`
- Deprecated `Inflater.input` property setter in favor of `Decompressor.setInput`

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
