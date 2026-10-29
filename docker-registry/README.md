# Docker Registry binaries

Run `make build` in this directory to build Distribution for Linux and macOS on
amd64 and arm64. Each ZIP contains a `registry.<os>-<arch>` executable.

Configure the Go image, upstream release and commit, LocalStack revision, and
optional dependency patches in `Dockerfile`. `GO_MODULE_OVERRIDES` accepts a
space-separated list of `module@version` entries; leave it empty to skip patches.

Before publishing, extract the archives with `unzip` and check the binaries:

```sh
go version -m <binary>
trivy rootfs --scanners vuln --severity HIGH,CRITICAL <directory>
```

When updating LocalStack, pin the artifacts commit in both registry installers
and their fallback URLs, and bump the package version to invalidate cached binaries.
