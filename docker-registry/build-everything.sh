#!/bin/bash
systems=( linux darwin )
architectures=( amd64 arm64 )

if [[ ! -d "distribution" ]]
then
git clone --depth 1 https://github.com/distribution/distribution.git
else
(cd distribution && git pull)
fi
for os in "${systems[@]}"
do
    for arch in "${architectures[@]}"
    do
        echo "Building registry for os $os and arch $arch"
        env GOOS=$os GOARCH=$arch ./build-registry.sh
    done
done
