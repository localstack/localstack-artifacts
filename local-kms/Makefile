GOPATH ?= ~/go

usage:            ## Show this help
	@fgrep -h "##" $(MAKEFILE_LIST) | fgrep -v fgrep | sed -e 's/\\$$//' | sed -e 's/##//'

build:            ## Download and build kms-local
	make build-alpine
	if uname -a | grep Darwin; then make build-osx; fi

build-alpine:
	mkdir -p build
	docker pull nsmithuk/local-kms
	docker run --entrypoint= nsmithuk/local-kms cat /usr/local/bin/local-kms > build/local-kms.alpine.bin
	chmod +x build/local-kms.alpine.bin

build-osx:
	mkdir -p build
	go get github.com/golang/dep/cmd/dep
	go get github.com/nsmithuk/local-kms
	cd $(GOPATH)/src/github.com/nsmithuk/local-kms && go install
	cp $(GOPATH)/bin/local-kms build/local-kms.osx.bin
	chmod +x build/local-kms.osx.bin

.PHONY: build
