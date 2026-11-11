@echo off
rem Compiles the Java version into java\build.
cd /d "%~dp0"
if not exist build mkdir build
javac -encoding UTF-8 -d build src\tds\*.java && echo compiled into java\build
