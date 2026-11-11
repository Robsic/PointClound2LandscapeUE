#!/bin/sh
# Compiles the Java version into java/build.
cd "$(dirname "$0")" && mkdir -p build && javac -encoding UTF-8 -d build src/tds/*.java && echo "compiled into java/build"
