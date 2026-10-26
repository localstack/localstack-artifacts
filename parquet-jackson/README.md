# Parquet Jackson with a patched Jackson

A rebuild of Apache Parquet's `parquet-jackson` module: Jackson (core, databind and annotations), relocated to `shaded.parquet.com.fasterxml.jackson`, with the same shading configuration as Parquet's own module (https://github.com/apache/parquet-java/blob/apache-parquet-1.13.1/parquet-jackson/pom.xml).

The only difference is the Jackson version. Spark 3.5.7 bundles `parquet-jackson-1.13.1.jar`, which shades Jackson 2.13.4. No `parquet-jackson` release shades a Jackson version with all CVE fixes yet, so LocalStack replaces it with `parquet-jackson-1.13.1-jackson-2.18.12.jar`, which shades Jackson 2.18.12. The classes `parquet-hadoop` 1.13.1 references under the shaded package all exist in Jackson 2.18.

## Building

```
mvn package
cp target/parquet-jackson-1.13.1-jackson-2.18.12.jar .
```
