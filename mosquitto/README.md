Prebuilt Mosquitto binaries used by LocalStack.

<https://mosquitto.org/>

To cross-build on x86\_64, following QEMU packages are required:

```
sudo apt-get install -y gcc-arm-linux-gnueabihf libc6-dev-armhf-cross qemu-user-static qemu-system-i386
```

Do not replace existing binaries. Instead bump the release versions.
