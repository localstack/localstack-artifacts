build: clean
	docker build -t registry-builder .
	docker run --rm -v $$PWD:/registries/dist registry-builder

clean:
	rm -f registry.*-*.zip


.PHONY: build
