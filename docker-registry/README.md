# Docker Registry binaries

These archives provide the registry used by LocalStack's ECR emulator for Linux and macOS,
on amd64 and arm64. Each archive contains one `registry.<os>-<arch>` executable.

## Build

Run `make build` in this directory. Docker cross-compiles all four binaries with CGO disabled.
The build pins Distribution v3.1.2 to commit
`3220848f15d9279c66a41aa0a257469e6aede1e9` and reports version
`v3.1.2-localstack.1`.

Security updates beyond the upstream release:

- Go 1.26.10 (including CVE-2026-111943).
- `golang.org/x/net` v0.60.0 (CVE-2026-78669).
- `github.com/klauspost/compress` v1.18.7 (CVE-2026-63209).

The script stops on errors and rejects an existing checkout at a different commit.
Review and remove the dependency overrides when a future upstream release includes them.

## Validate and consume

Extract the archives and inspect their build metadata with `go version -m`. Scan the
extracted Linux binaries with `trivy rootfs --scanners vuln --severity HIGH,CRITICAL`;
`trivy filesystem` does not inspect compiled Go binaries by default.

After publishing an artifacts commit, pin that commit in both LocalStack installers:
`localstack-core/localstack/lpm/installers/registry.py` and
`localstack-core/localstack/packages/registry.py`, including their fallback URLs.
Keep their package versions aligned so old cached binaries are not reused.
