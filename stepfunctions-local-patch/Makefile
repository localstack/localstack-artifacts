
usage:		## Show this help
	@fgrep -h "##" $(MAKEFILE_LIST) | fgrep -v fgrep | sed -e 's/\\$$//' | sed -e 's/##//'

extract:	## Extract stepfunctions libs from docker image
	rm -rf ./lib ; \
	docker_id=$$(docker run -d --entrypoint="" amazon/aws-stepfunctions-local:1.7.9 /bin/sleep infinity) ; \
	docker cp $$docker_id:/home/stepfunctionslocal/ ./lib ; \
	docker kill $$docker_id ; \
	wget -O lib/aspectjrt-1.9.7.jar https://repo1.maven.org/maven2/org/aspectj/aspectjrt/1.9.7/aspectjrt-1.9.7.jar; \
	wget -O lib/aspectjweaver-1.9.7.jar https://repo1.maven.org/maven2/org/aspectj/aspectjweaver/1.9.7/aspectjweaver-1.9.7.jar; \
	wget -O lib/kryo-5.2.0.jar https://repo1.maven.org/maven2/com/esotericsoftware/kryo/5.2.0/kryo-5.2.0.jar; \
	wget -O lib/objenesis-3.2.jar https://repo1.maven.org/maven2/org/objenesis/objenesis/3.2/objenesis-3.2.jar; \
	wget -O lib/minlog-1.3.1.jar https://repo1.maven.org/maven2/com/esotericsoftware/minlog/1.3.1/minlog-1.3.1.jar; \
	wget -O lib/reflectasm-1.11.9.jar https://repo1.maven.org/maven2/com/esotericsoftware/reflectasm/1.11.9/reflectasm-1.11.9.jar

compile:	## Compile the patch class(es)
	# Note: libs need to be added to lib folder first!
	javac -g -cp "lib/*" `find . -name '*.java'`

patch-jar:	## Create the patched JAR with updated classes
	make compile
	cp lib/StepFunctionsLocal.jar .
	# command below should be run with older Java8 jar command
	jar ufm StepFunctionsLocal.jar META-INF/MANIFEST.MF cloud/ com/ META-INF/aop.xml

compile-docker:	## Compile patch class(es) in Docker with JDK Version 8
	docker run --rm -it -v $$(pwd):/app openjdk:8 /bin/bash -c "javac -g -cp \"/app/lib/*\" \`find /app -name '*.java'\`"

