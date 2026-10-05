#!/bin/sh
# Manual build without Maven. Requires JDK 17+ and lib/ojdbc8.jar
set -e
mkdir -p out
javac -d out $(find src/main/java -name "*.java")
java -cp "out:lib/ojdbc8.jar" com.hospital.HospitalApp
