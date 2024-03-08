FUNCTIONTRACE_SERVER_VERSION := 0.7.1

all: amd64/functiontrace-server

amd64/functiontrace-server: amd64/bin/functiontrace-server
	cp $< $@
	strip $@

amd64/bin/functiontrace-server:
	cargo install --root amd64 --target x86_64-unknown-linux-musl functiontrace-server

arm64/functiontrace-server: arm64/aarch64-unknown-linux-musl/release/functiontrace-server
	cp $< $@

arm64/aarch64-unknown-linux-musl/release/functiontrace-server: functiontrace-server.tar.gz
	tar -xf $<
	cd functiontrace-server-$(FUNCTIONTRACE_SERVER_VERSION) && \
		cargo update && \
		CARGO_TARGET_DIR=$(PWD)/arm64 cross build --locked --release --target aarch64-unknown-linux-musl

functiontrace-server.tar.gz:
	curl -Lo $@ https://crates.io/api/v1/crates/functiontrace-server/$(FUNCTIONTRACE_SERVER_VERSION)/download
