
usage:		## Show this help
	@fgrep -h "##" $(MAKEFILE_LIST) | fgrep -v fgrep | sed -e 's/\\$$//' | sed -e 's/##//'

extract:	## Extract stepfunctions libs from docker image
	rm -rf ./lib ; \
	docker_id=$$(docker run -d --entrypoint="" amazon/aws-stepfunctions-local:latest /bin/sleep infinity) ; \
	docker cp $$docker_id:/home/stepfunctionslocal/ ./lib ; \
	docker kill $$docker_id ;

compile:	## Compile the patch class(es)
	# Note: libs need to be added to lib folder first!
	javac -cp "lib/*" `find . -name '*.java'`

compile-docker:	## Compile patch class(es) in Docker with JDK Version 8
	docker run --rm -it -v $$(pwd):/app openjdk:8 /bin/bash -c "javac -cp \"/app/lib/*\" \`find /app -name '*.java'\`"

