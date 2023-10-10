#!/bin/bash

rm -rf build/
mkdir build
(
  cd build
  wget https://aws-glue-etl-artifacts.s3.amazonaws.com/release/com/amazonaws/AWSGlueReaders/4.0.0/AWSGlueReaders-4.0.0.jar
)

mvn compile

(
  cd target/classes/
  zip -r ../../build/AWSGlueReaders-4.0.0.jar com
)

echo "Patched JAR file has been built: build/AWSGlueReaders-4.0.0.jar"
