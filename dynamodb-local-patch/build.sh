#!/bin/bash

test -e dynamodb_local_latest.zip || \
  wget https://s3.us-west-2.amazonaws.com/dynamodb-local/dynamodb_local_latest.zip

# prepare contents
rm -rf build/
mkdir -p build/DynamoDBLocal_lib
cp -r etc/packages/* build/DynamoDBLocal_lib
cp dynamodb_local_latest.zip build/

# Unzip package
(
  cd build
  unzip dynamodb_local_latest.zip
  rm dynamodb_local_latest.zip
)

# Patch jetty
(
  cd build/DynamoDBLocal_lib
  classes=$(ls | tr "\n" ":")
  package_name=$(ls | grep jetty-http-*.jar)
  javac -cp ${classes} org/eclipse/jetty/http/MimeTypes.java
  zip -r ${package_name} org/
)

# remove unused libs
(
  cd build
  rm -f DynamoDBLocal_lib/libsqlite4java-linux-aarch64.so
  rm -f DynamoDBLocal_lib/libsqlite4java-linux-i386.so
  rm -f DynamoDBLocal_lib/libsqlite4java-osx.dylib
  rm -f DynamoDBLocal_lib/sqlite4java-win32-x64.dll
  rm -f DynamoDBLocal_lib/sqlite4java-win32-x86.dll
)

cp etc/libsqlite4java-linux-amd64.so build/DynamoDBLocal_lib/

# create zip file
(cd build; zip -r DynamoDBLocal.zip *)
cp build/DynamoDBLocal.zip etc/
