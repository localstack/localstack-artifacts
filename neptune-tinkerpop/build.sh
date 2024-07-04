#!/bin/bash

runDir=$(pwd)

versions=("3.4.11" "3.5.2" "3.6.2" "3.6.5" "3.7.1" "3.7.2")
for version in "${versions[@]}"; do
  (
    cd $version
    pwd
    mvn package
    (cd target/classes; zip -r $runDir/gremlin-core-$version-patches.zip *)
  )
done
