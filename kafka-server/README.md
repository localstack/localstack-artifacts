# Local Embedded Kafka Server

## Prerequisites

* Java
* Gradle

## Building

To build the latest version of the embedded server, run this command:
```
make build
```

On successful build, this will generate a file `build/libs/kafka-server-all.jar` which should then be renamed to `kafka-server-all-<version>.jar` (e.g., `kafka-server-all-3.1.0.jar`) and pushed to the `localstack-assets` S3 bucket.
