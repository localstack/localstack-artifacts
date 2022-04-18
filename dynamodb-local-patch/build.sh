#!/bin/bash

test -e dynamodb_local_latest.zip || \
  wget https://s3.us-west-2.amazonaws.com/dynamodb-local/dynamodb_local_latest.zip

# prepare contents
rm -rf build/
mkdir -p build
cp dynamodb_local_latest.zip build/
(
  cd build
  unzip dynamodb_local_latest.zip
  rm dynamodb_local_latest.zip
  # remove unused libs
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

# compile patched Java class
javac -cp build/DynamoDBLocal.jar `find com/ -name '*.java'`
