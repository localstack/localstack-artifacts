IMAGE_NAME := dropbear_ssh_builder
CONTAINER_NAME := dropbear_ssh_runner

.PHONY: all
all: amd64 arm64

.PHONY: amd64 arm64
amd64 arm64:
	mkdir -p $@
	docker build --platform linux/$@ -t $(IMAGE_NAME) .
	docker run --platform linux/$@ -it --rm -d --name $(CONTAINER_NAME) $(IMAGE_NAME) bash
	docker cp $(CONTAINER_NAME):/tmp/dropbear/dropbear $@/dropbear
	chmod +x $@/dropbear
	docker cp $(CONTAINER_NAME):/tmp/dropbear/scp $@/scp
	chmod +x $@/scp
	docker stop $(CONTAINER_NAME)
