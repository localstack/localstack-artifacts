
usage:        ## Show this help
	@fgrep -h "##" $(MAKEFILE_LIST) | fgrep -v fgrep | sed -e 's/\\$$//' | sed -e 's/##//'

compile:      ## Compile the patch class(es)
	# Note: libs need to be added to lib folder first!
	javac -cp "lib/*" `find . -name '*.java'`
