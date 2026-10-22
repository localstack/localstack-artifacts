FROM golang:1.26.10-trixie
RUN apt-get update && apt-get install -y --no-install-recommends zip && rm -rf /var/lib/apt/lists/*
WORKDIR /registries
COPY build-registries.sh .
CMD ["./build-registries.sh"]
