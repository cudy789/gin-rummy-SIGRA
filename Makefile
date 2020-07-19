.PHONY: build run

build:
	javac **/*.java

run: build
	java ginrummy.GinRummyGame
