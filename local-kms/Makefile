GOPATH ?= ~/go

usage:            ## Show this help
	@fgrep -h "##" $(MAKEFILE_LIST) | fgrep -v fgrep | sed -e 's/\\$$//' | sed -e 's/##//'

build:            ## Download and build kms-local
	make build-alpine
	make build-linux
	make build-windows
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

build-linux:
	docker run --rm --name kms_tmp -d -it golang:stretch bash
	docker exec kms_tmp go get github.com/golang/dep/cmd/dep
	docker exec kms_tmp go get github.com/nsmithuk/local-kms
	docker exec kms_tmp cd /go/src/github.com/nsmithuk/local-kms && go install
	docker exec kms_tmp sed -i 's/.*uuid.NewV4().String().*/keyIdObj, _ := uuid.NewV4(); keyId := keyIdObj.String()/' \
		src/github.com/nsmithuk/local-kms/src/handler/create_key.go
	docker exec kms_tmp bash -c 'cd /go/src/github.com/nsmithuk/local-kms && go install'
	docker exec kms_tmp cat /go/bin/local-kms > build/local-kms.linux.bin
	docker rm -f kms_tmp

build-windows:
	mkdir -p build
	go get github.com/golang/dep/cmd/dep
	go get github.com/nsmithuk/local-kms
	go get github.com/konsorten/go-windows-terminal-sequences
	cd $(GOPATH)/src/github.com/nsmithuk/local-kms && GOOS=windows GOARCH=amd64 go build -o build/local-kms.windows.bin start.go
	chmod +x build/local-kms.windows.bin

.PHONY: build
