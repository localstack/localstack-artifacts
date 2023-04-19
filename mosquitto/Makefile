IMAGE_NAME := mosquitto_builder
CONTAINER_NAME := mosquitto_runner
VERSION := v2.0.12

.PHONY: all
all: amd64 arm64

.PHONY: amd64 arm64
amd64 arm64:
	mkdir -p $(VERSION)/$@
	docker build --build-arg VERSION=$(VERSION) --platform linux/$@ -t $(IMAGE_NAME) .
	docker run --platform linux/$@ -it --rm -d --name $(CONTAINER_NAME) $(IMAGE_NAME) bash
	docker cp $(CONTAINER_NAME):/tmp/mosquitto/src/mosquitto $(VERSION)/$@/mosquitto
	chmod +x $(VERSION)/$@/mosquitto
	docker stop $(CONTAINER_NAME)
