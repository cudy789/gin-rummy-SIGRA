.PHONY: build run

#Thanks to https://stackoverflow.com/questions/10382929/how-to-fix-java-lang-unsupportedclassversionerror-unsupported-major-minor-versi
#  for notes on compiling for backwards compatibility without changing the compiler
#...The above failed with a new error, and thanks to https://stackoverflow.com/questions/23627606/target-different-version-of-jre
#  for addressing that -target 1.8 changes to --release 8
version=8

build:
	mkdir bin/
	javac --release $(version) -d bin/ **/*.java

clean:
	rm -r bin/

run: build results
	java ginrummy.GinRummyGame

docker:
	docker build . -t jmmaloney4/sigra:latest
	docker push jmmaloney4/sigra:latest
