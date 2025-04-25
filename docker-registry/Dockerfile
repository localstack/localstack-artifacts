FROM golang:1.24-bookworm
RUN apt-get update && apt-get install -y zip
WORKDIR /registries
COPY build-registries.sh .
CMD ["./build-registries.sh"] 
