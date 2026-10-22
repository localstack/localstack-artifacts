#!/bin/bash
set -euo pipefail

# Distribution v3.1.2, pinned so rebuilding does not silently change upstream source.
readonly registry_commit=3220848f15d9279c66a41aa0a257469e6aede1e9
readonly registry_version=v3.1.2-localstack.1

if [[ ! -d distribution/.git ]]; then
    git clone --branch v3.1.2 --depth 1 https://github.com/distribution/distribution.git
fi
cd distribution
if [[ $(git rev-parse HEAD) != "$registry_commit" ]]; then
    echo "Expected distribution commit $registry_commit" >&2
    exit 1
fi

# Patch CVE-2026-78669 (x/net) and CVE-2026-63209 (compress) pending upstream release.
go get golang.org/x/net@v0.60.0 github.com/klauspost/compress@v1.18.7
go mod vendor

mkdir -p ../dist
for os in linux darwin; do
    for arch in amd64 arm64; do
        echo "Building registry for os $os and arch $arch"
        GOOS=$os GOARCH=$arch CGO_ENABLED=0 make bin/registry VERSION="$registry_version"
        registry_file_name=registry.$os-$arch
        cp bin/registry "../$registry_file_name"
        (cd .. && zip -X "dist/$registry_file_name.zip" "$registry_file_name")
        rm "../$registry_file_name"
    done
done
