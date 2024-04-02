# Local Embedded Kafka Server

## Prerequisites

* Java
* Gradle

## Building

Note: recent versions of [`kafka-embedded-env`](https://github.com/navikt/kafka-embedded-env) require Java 17+, whereas we only have JRE 11 installed in the LocalStack container.
Hence, we first need to check out and patch the repo locally:
```
make patch
```

To build the latest version of the embedded server, run this command:
```
make build
```

On successful build, this will generate a file `build/libs/kafka-server-all.jar` which should then be renamed to `kafka-server-all-<version>.jar` (e.g., `kafka-server-all-3.1.0.jar`) and pushed to the `localstack-assets` S3 bucket.

## Upgrading

To upgrade to a newer version, follow these steps:

* Adjust `KAFKA_EMBEDDED_ENV_VERSION` and `KAFKA_EMBEDDED_ENV_COMMIT` in `Makefile`
* Adjust the version in `implementation("no.nav:kafka-embedded-env:<version>")` in `build.gradle`
* Run `make clean` to ensure that local patches are cleaned up
* Run `make patch` and `make build` as per instructions further above
