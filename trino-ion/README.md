# Trino Hive plugin with Ion support

Trino's Hive connector can't read or write [Amazon Ion](https://amazon-ion.github.io/ion-docs/) tables (`com.amazon.ionhiveserde.IonHiveSerDe`). This directory has Trino 484's two Hive plugin jars rebuilt with native Ion support, which LocalStack installs so that Athena can query Ion tables.

The Ion code comes from [trinodb/trino#24511](https://github.com/trinodb/trino/pull/24511), "Native Trino Ion Format Integration", by Amazon's Ion team. That PR was closed unmerged in May 2025. `patches/ion-port-484.patch` is its main code, without tests, at head `640f39ed8377c10bff85e670826ca08d5bda772e`, ported to Trino 484.

## Contents

- `484/trino-hive-484.jar` and `484/io.trino_trino-hive-formats-484.jar`: replacements for the files of the same name in `plugin/hive/` of `trino-server-484.tar.gz`.
- `patches/ion-port-484.patch`: the port, which `git apply` applies to Trino's tag `484`.
- `build.sh` and `repack.py`: the build.

The jars also need these two jars in `plugin/hive/`, from Maven Central:

- `com.amazon.ion:ion-java:1.11.9`
- `com.amazon.ion:ion-java-path-extraction:1.5.0`. Its POM declares kotlin-stdlib, but the jar doesn't use Kotlin.

In the tarball, the two jars in `plugin/hive/` are hard links to the ones in `plugin/delta-lake/`, `plugin/hudi/`, `plugin/iceberg/` and `plugin/lakehouse/`. Delete the old file before you copy the new one, or write a new file and rename it over the old one. Copying over the old file changes the other plugins too.

With the jars installed, the Hive connector reads tables with the Ion SerDe, in text or binary Ion, compressed or not. It reads them as Amazon Athena reads them through the Ion Hive SerDe; see [Reading as Athena does](#reading-as-athena-does). It writes Ion tables, such as those created with `format = 'ION'`, as Athena writes them through the SerDe; see [Writing as Athena does](#writing-as-athena-does). Reads ignore the writer properties, as the SerDe does.

## What the patch changes

New classes from the upstream PR:

- `io.trino.hive.formats.ion` in `trino-hive-formats`: `IonDecoder`, `IonDecoderConfig` and `IonEncoder`. `IonEncoderConfig` and `IonSerDeErrors` are this repository's.
- `io.trino.plugin.hive.ion` in `trino-hive`: `IonPageSource`, `IonPageSourceFactory`, `IonFileWriter`, `IonFileWriterFactory` and `IonSerDeProperties`.

Changes to existing classes:

- `HiveClassNames`: the Ion SerDe, input format and output format class names.
- `HiveStorageFormat`: an `ION` format, which isn't splittable.
- `HiveFormatsModule`: binds the Ion page source and file writer factories.
- `HiveWriterFactory`: compressed Ion files get the compression's extension, as text files do. An Ion file is named for the compression the table's `write.compression` names.

The PR targets Trino 475-SNAPSHOT. Three things differ in 484:

1. The format bindings are in `HiveFormatsModule`, not `HiveModule`.
2. `HiveStorageFormat` has the new formats `SEQUENCEFILE_PROTOBUF`, `ESRI` and `ESRI_GEO_JSON`. `ION` goes last, and like the ESRI formats it isn't splittable.
3. `FileWriter.commit()` returns a `RollbackAction`. `IonFileWriter` and `IonFileWriterFactory` follow 484's Avro and line writers.

`IonEncoder` uses Trino's type accessors to read arrays and structs from dictionary blocks. Hive produces those blocks when it routes rows from a single page to multiple partitions.

## Reading as Athena does

Athena reads Ion tables with the Ion Hive SerDe (`com.amazon.ion:ion-hive3-serde`). The patch changes the upstream PR's reader to give the same values and errors, as recorded on Athena:

- **Nulls:** any Ion null, typed or not, reads as NULL, whatever the column's type.
- **Ion types:** a column reads only the Ion types the SerDe accepts. `double` and `real` read only floats, `decimal` reads integers and decimals, `varchar(n)` and `char(n)` read only strings and symbols, and `string` reads any value, as its ASCII Ion text if it is not a string or a symbol. A value of another type fails with `GENERIC_INTERNAL_ERROR` and the `ClassCastException` message the SerDe's cast gives. For `decimal`, it fails with `HIVE_BAD_DATA` and `Invalid Ion type`.
- **Values that don't fit:** an integer out of the column's range, a `real` that loses precision, text longer than `varchar(n)` or `char(n)`, and a decimal with more fraction digits than the column's scale fail with `HIVE_BAD_DATA` and the SerDe's message. With `ion.fail_on_overflow` or `ion.<column>.fail_on_overflow` set to anything but `true`, integers keep their low bits as a Java cast does, `real` values are rounded and text is truncated. A decimal with more digits than the column's precision fails with `NUMERIC_VALUE_OUT_OF_RANGE`. Athena returns that decimal, but Trino can't send it to its clients.
- **Timestamps:** a timestamp reads as its UTC instant, truncated to milliseconds. A `date` reads as the UTC date of that instant.
- **Malformed Ion:** it fails with `HIVE_CURSOR_ERROR` and ion-java's message. With `ion.ignore_malformed=true`, the rest of the file is skipped from the first malformed value on.
- **Path extractors:** every table column registers its search path, in the iteration order of a `HashMap` keyed by column name, and the extractor matches as the SerDe's does. Columns on the same path all read a scalar. Only the first one reads a container, and the others read NULL. A column whose path lies under another column's path reads NULL. The struct fields of a column match case-insensitively, unless `ion.path_extractor.case_sensitive` is `true`. An exact match wins over a case-insensitive one. The upstream PR's `ion.path_extractor.strict` property is gone.

The SerDe's errors are reported as Athena reports them: `HIVE_BAD_DATA` messages start with `Error Parsing a column in the table: `, and `GENERIC_INTERNAL_ERROR` is an external error, not an internal one.

## Writing as Athena does

Athena writes Ion tables with the same SerDe. The patch changes the upstream PR's writer to write the same files, as recorded on Athena:

- **Encoding:** binary Ion, or text Ion if the table's `ion.encoding` is `TEXT`. The SerDe reads the property as the name of its `IonEncoding` constant, in upper case. Any other value fails the write with `GENERIC_INTERNAL_ERROR: No enum constant com.amazon.ionhiveserde.configuration.IonEncoding.<value>`.
- **Rows:** each row is an Ion stream of its own. In binary, each row has its own version marker and symbol table. In text, the rows follow each other without a separator, and without a version marker.
- **Compression:** the codec the table's `write.compression` names, in any case: `NONE`, `GZIP`, `ZSTD`, `SNAPPY`, `BZIP2` or `LZ4`. Without the property, the session's `compression_codec` applies. Any other value fails the write with `HIVE_UNSUPPORTED_FORMAT: Failed to load compression codec: <value>`. Compressed files get the codec's extension.
- **Nulls:** `ion.serialize_null` says how a NULL struct field or map value is written: `OMIT` (the default) leaves it out, `UNTYPED` writes `null`, and `TYPED` writes the typed null of the column's type, such as `null.int`. In a list, a NULL struct is written as a struct whose fields are all NULL, and a NULL list as an empty list. Other NULL elements are written as typed nulls. For a string or a blob, so does the SerDe; for other types, the SerDe fails. Any other `ion.serialize_null` fails with `No enum constant com.amazon.ionhiveserde.configuration.SerializeNullStrategy.<value>`.
- **Timestamps:** written in milliseconds, at the offset `ion.timestamp.serialization_offset` gives (`Z` by default). The offset is `Z` or `[+|-]HH:MM`; without a sign it is positive. Other values fail with the SerDe's `GENERIC_INTERNAL_ERROR` messages. A `date` is written as a timestamp of day precision.
- **Decimals:** written as Hive holds them, without trailing zeros in the fraction.
- **Maps:** a map whose keys are not strings fails with the `ClassCastException` of the SerDe's cast of their object inspector to a string one.
- **Columns:** `ion.<column>.serialize_as` is ignored.

The writer properties are read when a write opens its first file, so a write of no rows succeeds whatever they hold.

## Building

```
./build.sh
git status    # no changes: the build reproduced the committed jars
```

The first run caches its downloads in `build/`. The Hive plugin ZIP is about 230 MB. The build requires Python 3.9 or later.

1. Downloads the official `trino-hive-484.zip`, the four Trino sources the patch changes, the two Ion jars, three compile-time dependencies from Maven Central and Temurin JDK 25.0.5+8. Each download is pinned by its SHA-256.
2. Applies the patch and compiles the 14 changed and new sources with `javac --release 25 -g -parameters`. The compile-time dependencies supplement the plugin ZIP with Trino SPI, Airlift Slice and Jackson annotations.
3. Replaces the compiled classes in the two Hive plugin jars with Python's standard `zipfile` module. The entry contents outside the replaced classes are preserved. Fixed timestamps and uncompressed entries keep the build independent of the local zlib. The resulting jars total about 5 MB.

Recompiling only these classes is safe:

- `HiveClassNames` only gains constants, so the constants compiled into other classes are unchanged.
- `ION` is the last `HiveStorageFormat` constant, so the other constants keep their ordinals. The only other `switch` over `HiveStorageFormat` in the plugin, in `HiveSplitManager.isPartitionUsesColumnNames`, has a `default` branch.

## Partitioned write regression test

Start a Trino 484 server with the patched Hive jars, the two Ion dependencies and a writable `hive` catalog. Run:

```sh
python3 test_partitioned_write.py http://localhost:8080
```

The test inserts arrays and structs into two partitions in a single statement, checks the values returned by Trino and removes its temporary schema.

## Porting to another Trino version

1. In a Trino checkout at the new tag, run `git apply --3way patches/ion-port-484.patch`. Resolve the conflicts and save the result as `patches/ion-port-<version>.patch`.
2. Check that recompiling only the patched classes is still safe, as described above. A new `switch` over `HiveStorageFormat` without a `default` branch must be recompiled too.
3. In `build.sh`, update `TRINO_VERSION`, `TRINO_COMMIT`, `TRINO_HIVE_SHA256`, the hashes in `TRINO_SOURCES` and the versions and hashes in `TRINO_COMPILE_DEPS`. Update the JDK if Trino needs a newer Java. The script writes the jars to `<version>/`.
4. javac warns about two deprecated APIs that a later Trino may remove: `IonPageSourceFactory` overrides the `HivePageSourceFactory.createPageSource` variant without a `MemoryContext`, and `IonPageSource` overrides `ConnectorPageSource.getMemoryUsage()`.

## License

Trino and the Ion code are licensed under the Apache License 2.0. The jars are Trino 484's jars with the changes described above.
