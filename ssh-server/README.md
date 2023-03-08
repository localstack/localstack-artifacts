To cross-build on x86\_64, following QEMU packages are required:

```
sudo apt-get install -y gcc-arm-linux-gnueabihf libc6-dev-armhf-cross qemu-user-static qemu-system-i386
```

The following files exist to maintain backward compatibility in released LocalStack versions.

- [./dropbear](./dropbear)
- [./scp](./dropbear)
