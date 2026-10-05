@echo off
rem Manual build without Maven. Requires JDK 17+ and lib\ojdbc8.jar
if not exist out mkdir out
dir /s /b src\main\java\*.java > sources.txt
javac -d out @sources.txt || exit /b 1
java -cp "out;lib\ojdbc8.jar" com.hospital.HospitalApp
