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

With the jars installed, the Hive connector reads tables with the Ion SerDe, in text or binary Ion, compressed or not. It writes Ion tables, such as those created with `format = 'ION'`, as binary Ion, or as text Ion if the table's SerDe property `ion.encoding` is `TEXT`. Reads and writes fail with `HIVE_UNSUPPORTED_FORMAT` if the table sets `ion.fail_on_overflow`, `ion.ignore_malformed`, `ion.timestamp.serialization_offset` or `ion.serialize_null` to a non-default value, or sets any `ion.<column>.fail_on_overflow` or `ion.<column>.serialize_as`.

## What the patch changes

New classes, unchanged from the PR:

- `io.trino.hive.formats.ion` in `trino-hive-formats`: `IonDecoder`, `IonDecoderConfig` and `IonEncoder`.
- `io.trino.plugin.hive.ion` in `trino-hive`: `IonPageSource`, `IonPageSourceFactory`, `IonFileWriter`, `IonFileWriterFactory` and `IonSerDeProperties`.

Changes to existing classes:

- `HiveClassNames`: the Ion SerDe, input format and output format class names.
- `HiveStorageFormat`: an `ION` format, which isn't splittable.
- `HiveFormatsModule`: binds the Ion page source and file writer factories.
- `HiveWriterFactory.getFileExtension`: compressed Ion files get the compression's extension, as text files do.

The PR targets Trino 475-SNAPSHOT. Three things differ in 484:

1. The format bindings are in `HiveFormatsModule`, not `HiveModule`.
2. `HiveStorageFormat` has the new formats `SEQUENCEFILE_PROTOBUF`, `ESRI` and `ESRI_GEO_JSON`. `ION` goes last, and like the ESRI formats it isn't splittable.
3. `FileWriter.commit()` returns a `RollbackAction`, not a `Closeable`. `IonFileWriter` and `IonFileWriterFactory` follow 484's Avro and line writers.

## Building

```
./build.sh
git status    # no changes: the build reproduced the committed jars
```

The first run downloads about 1 GB into `build/`, which later runs reuse. The script:

1. Downloads the official `trino-server-484.tar.gz`, the four Trino sources the patch changes (at the commit of tag `484`), the two Ion jars and Temurin JDK 25.0.5+8. Each download is pinned by its sha256.
2. Applies the patch. Then it compiles the 12 changed and new sources with `javac --release 25 -g -parameters`, against the jars in the tarball's `plugin/hive/` and `lib/`.
3. Replaces those classes in the tarball's two jars with `repack.py`. All other entries are copied byte for byte. The new classes are stored uncompressed, so the output doesn't depend on the local zlib.

Recompiling only these classes is safe:

- `HiveClassNames` only gains constants, so the constants compiled into other classes are unchanged.
- `ION` is the last `HiveStorageFormat` constant, so the other constants keep their ordinals. The only other `switch` over `HiveStorageFormat` in the plugin, in `HiveSplitManager.isPartitionUsesColumnNames`, has a `default` branch.

## Porting to another Trino version

1. In a Trino checkout at the new tag, run `git apply --3way patches/ion-port-484.patch`. Resolve the conflicts and save the result as `patches/ion-port-<version>.patch`.
2. Check that recompiling only the patched classes is still safe, as described above. A new `switch` over `HiveStorageFormat` without a `default` branch must be recompiled too.
3. In `build.sh`, update `TRINO_VERSION`, `TRINO_COMMIT`, `TRINO_TARBALL_SHA256` and the sha256s in `TRINO_SOURCES`. Update the JDK if Trino needs a newer Java. The script writes the jars to `<version>/`.
4. javac warns about two deprecated APIs that a later Trino may remove: `IonPageSourceFactory` overrides the `HivePageSourceFactory.createPageSource` variant without a `MemoryContext`, and `IonPageSource` overrides `ConnectorPageSource.getMemoryUsage()`.

## License

Trino and the Ion code are licensed under the Apache License 2.0. The jars are Trino 484's jars with the changes described above.
