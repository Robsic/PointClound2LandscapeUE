#!/bin/sh
# Compiles the first version into first/java/build.
cd "$(dirname "$0")/java" && mkdir -p build && javac -nowarn -encoding UTF-8 -d build src/RunFirst.java src/interpolacoes/inter/*.java src/interpolacoes/proposed/*.java && echo "compiled into first/java/build"
