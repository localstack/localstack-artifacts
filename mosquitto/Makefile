IMAGE_NAME := mosquitto_builder
CONTAINER_NAME := mosquitto_runner
VERSION := v2.0.12
DEB_RELEASE := bookworm

.PHONY: all
all: amd64 arm64

.PHONY: amd64 arm64
amd64 arm64:
	mkdir -p $(DEB_RELEASE)/$(VERSION)/$@
	docker build \
		--build-arg VERSION=$(VERSION) \
		--build-arg DEB_RELEASE=$(DEB_RELEASE) \
		--platform linux/$@ \
		-t $(IMAGE_NAME) .
	docker run \
		--platform linux/$@ \
		-it --rm -d --name $(CONTAINER_NAME) $(IMAGE_NAME) bash
	docker cp $(CONTAINER_NAME):/tmp/mosquitto/src/mosquitto $(DEB_RELEASE)/$(VERSION)/$@/mosquitto
	chmod +x $(DEB_RELEASE)/$(VERSION)/$@/mosquitto
	docker stop $(CONTAINER_NAME)
