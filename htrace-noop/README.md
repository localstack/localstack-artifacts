# Htrace Noop Implementation

This project is a simple htrace no-op implementation, taken from Hadoop common utils: https://github.com/apache/hadoop/tree/b1ed23654c01052074ea81fadb685d2ea7bb4bfa/hadoop-common-project/hadoop-common/src/main/java/org/apache/hadoop/tracing

The `htrace-noop-0.1.jar` JAR file in this is folder is required to fix CVEs in certain third-party libraries (e.g., Hive) used in LocalStack.

## Building

```
mvn package
cp target/htrace-noop-0.1.jar .
```
