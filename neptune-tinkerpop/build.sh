#!/bin/bash

runDir=$(pwd)

versions=("3.6.2" "3.7.2")
for version in "${versions[@]}"; do
  (
    cd $version
    pwd
    mvn package
    (cd target/classes; zip -r $runDir/gremlin-core-$version-patches.zip *)
  )
done
