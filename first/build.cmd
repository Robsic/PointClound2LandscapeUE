@echo off
rem Compiles the first version into first\java\build.
cd /d "%~dp0java"
if not exist build mkdir build
javac -nowarn -encoding UTF-8 -d build src\RunFirst.java src\interpolacoes\inter\*.java src\interpolacoes\proposed\*.java && echo compiled into first\java\build
