#!/bin/bash
(cd distribution && make bin/registry)
registry_file_name=registry-$GOOS-$GOARCH
cp distribution/bin/registry ${registry_file_name}
zip ${registry_file_name}.zip ${registry_file_name}
(cd distribution && make clean)
