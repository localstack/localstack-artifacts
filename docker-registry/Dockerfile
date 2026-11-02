ARG GO_IMAGE=golang:1.26.10-trixie
FROM ${GO_IMAGE}

ENV REGISTRY_VERSION=v3.1.2 \
    REGISTRY_COMMIT=3220848f15d9279c66a41aa0a257469e6aede1e9 \
    LOCALSTACK_REVISION=2

ENV GO_MODULE_OVERRIDES="golang.org/x/net@v0.60.0 \
    github.com/klauspost/compress@v1.18.7 \
    github.com/go-jose/go-jose/v4@v4.1.6"

RUN apt-get update && apt-get install -y zip
WORKDIR /registries
COPY build-registries.sh .
CMD ["./build-registries.sh"]
