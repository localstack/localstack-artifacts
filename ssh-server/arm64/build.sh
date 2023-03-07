#!/bin/bash -eux

# Following QEMU packages are required for cross-building
# sudo apt-get install -y gcc-arm-linux-gnueabihf libc6-dev-armhf-cross qemu-user-static qemu-system-i386

docker build --platform linux/arm64/v8 -t tmp-ssh .
docker run --platform linux/arm64/v8 -it --rm -d --name tmp-ssh tmp-ssh sleep 9

docker cp tmp-ssh:/tmp/dropbear/dropbear dropbear
chmod +x dropbear

docker cp tmp-ssh:/usr/bin/scp scp
chmod +x scp
