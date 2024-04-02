KAFKA_EMBEDDED_ENV_VERSION ?= 3.2.7
KAFKA_EMBEDDED_ENV_COMMIT ?= a5a21c8c6593d30860160cff54ffd350f7e1a0d5

usage:        ## Show this help
	@fgrep -h "##" $(MAKEFILE_LIST) | fgrep -v fgrep | sed -e 's/\\$$//' | sed -e 's/##//'

patch:        ## Clone and patch the kafka-embedded-env repo, build code into local Maven repo
	test -e kafka-embedded-env || git clone https://github.com/navikt/kafka-embedded-env
	(cd kafka-embedded-env; git checkout $(KAFKA_EMBEDDED_ENV_COMMIT))
	# note: using perl instead of sed below, for better Linux/MacOS cross-compatibility
	perl -i -pe 's|<jvmTarget>17<|<jvmTarget>11<|g' kafka-embedded-env/pom.xml
	perl -i -pe 's|<maven\.compiler\.source>17<|<maven.compiler.source>11<|g' kafka-embedded-env/pom.xml
	perl -i -pe 's|<maven\.compiler\.target>17<|<maven.compiler.target>11<|g' kafka-embedded-env/pom.xml
	(cd kafka-embedded-env; mvn -B versions:set -DnewVersion="$(KAFKA_EMBEDDED_ENV_VERSION)" -DgenerateBackupPoms=false)
	(cd kafka-embedded-env; mvn install)

build:        ## Build the JAR file
	gradle shadowJar

clean:        ## Clean up local temporary files
	rm -rf kafka-embedded-env

.PHONY: usage build patch clean
