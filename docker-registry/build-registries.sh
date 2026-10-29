#!/bin/bash
set -euo pipefail

git clone --branch "$REGISTRY_VERSION" --depth 1 https://github.com/distribution/distribution.git
cd distribution
test "$(git rev-parse HEAD)" = "$REGISTRY_COMMIT"

go get "golang.org/x/net@${X_NET_VERSION}" \
    "github.com/klauspost/compress@${COMPRESS_VERSION}" \
    "github.com/go-jose/go-jose/v4@${GO_JOSE_VERSION}"
go mod vendor

mkdir -p ../dist
for os in linux darwin; do
    for arch in amd64 arm64; do
        echo "Building registry for os $os and arch $arch"
        registry_file_name=registry.$os-$arch
        GOOS=$os GOARCH=$arch CGO_ENABLED=0 make bin/registry VERSION="${REGISTRY_VERSION}-localstack.${LOCALSTACK_REVISION}" BINARY_SUFFIX=".$os-$arch"
        (cd bin && zip -X "../../dist/$registry_file_name.zip" "$registry_file_name")
    done
done
