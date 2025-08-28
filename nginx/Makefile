IMAGE_NAME := nginx-builder
NGINX_VERSION := 1.28.0

.PHONY: all
all: amd64 arm64

.PHONY: amd64 arm64
amd64 arm64:
	mkdir -p $(NGINX_VERSION)/$@
	docker build \
		--platform linux/$@ \
		--build-arg NGINX_VERSION=$(NGINX_VERSION) \
		-t $(IMAGE_NAME) .
	CONTAINER_ID=$$(docker run \
		--platform linux/$@ \
		-it --rm -d $(IMAGE_NAME) bash -c 'trap : TERM INT; sleep infinity & wait $$!') && \
	docker cp $$CONTAINER_ID:nginx $(NGINX_VERSION)/$@/nginx && \
	chmod +x $(NGINX_VERSION)/$@/nginx && \
	docker stop $$CONTAINER_ID
