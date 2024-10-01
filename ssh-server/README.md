To cross-build on x86\_64, following QEMU packages are required:

```
sudo apt-get install -y gcc-arm-linux-gnueabihf libc6-dev-armhf-cross qemu-user-static qemu-system-i386
```

Then you can simply run this to re-build the binaries:

```
make all
```

> [!NOTE]
> The following files are used by older versions of LocalStack where the download URL is hardcoded.
> Removing them will break these releases.
> 
> - [./dropbear](./dropbear)
> - [./scp](./dropbear)
