#!/bin/bash
systems=( linux darwin )
architectures=( amd64 arm64 )

build-registry () {
(cd distribution && make bin/registry)
local registry_file_name=registry.$GOOS-$GOARCH
cp distribution/bin/registry ${registry_file_name}
zip dist/${registry_file_name}.zip ${registry_file_name}
rm ${registry_file_name}
(cd distribution && make clean)
}

if [[ ! -d "distribution" ]]
then
git clone --depth 1 https://github.com/distribution/distribution.git
else
(cd distribution && git pull)
fi
mkdir -p dist
for os in "${systems[@]}"
do
    for arch in "${architectures[@]}"
    do
        echo "Building registry for os $os and arch $arch"
        export GOOS=$os
        export GOARCH=$arch
        build-registry
    done
done
