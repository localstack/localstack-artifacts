ARG GO_IMAGE=golang:1.26.10-trixie
FROM ${GO_IMAGE}

ENV REGISTRY_VERSION=v3.1.2 \
    REGISTRY_COMMIT=3220848f15d9279c66a41aa0a257469e6aede1e9 \
    LOCALSTACK_REVISION=2 \
    X_NET_VERSION=v0.60.0 \
    COMPRESS_VERSION=v1.18.7 \
    GO_JOSE_VERSION=v4.1.6

RUN apt-get update && apt-get install -y zip
WORKDIR /registries
COPY build-registries.sh .
CMD ["./build-registries.sh"]
