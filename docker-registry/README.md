# Docker Registry binaries

Run `make build` here to cross-compile Distribution v3.1.2 for Linux and macOS,
on amd64 and arm64. Each ZIP contains one executable named `registry.<os>-<arch>`.
The disposable build container uses Go 1.26.10 and the pinned upstream release commit,
with overrides for `golang.org/x/net` v0.60.0, `github.com/klauspost/compress` v1.18.7,
and `github.com/go-jose/go-jose/v4` v4.1.6. Binaries report `v3.1.2-localstack.2`.
The Go image, upstream release and commit, LocalStack revision, and dependency override
versions are defined together in `Dockerfile`.

Upstream v3.1.2 publishes only Linux binaries and still includes Go 1.26.8,
x/net v0.59.0, and compress v1.18.4, so it cannot replace this patched build.
Revisit the overrides when upgrading the upstream release.

Before publishing, extract each ZIP with `unzip`, inspect `go version -m <binary>`,
and scan with `trivy rootfs --scanners vuln --severity HIGH,CRITICAL <directory>`.
Preserve executable permissions: Trivy must report detecting all four Go binaries.

Pin the published artifacts commit in both LocalStack registry installers (including
fallback URLs), and bump their package versions together to invalidate old caches.
Scan the rebuilt LocalStack image before closing the security issues.
