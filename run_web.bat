@echo off
title ExPense Book - Responsive Web Application
echo ===================================================
echo   ExPense Book — Track ^• Analyse ^• Control
echo   Responsive Web Application (Any Device / Browser)
echo ===================================================
echo Starting ExPense Book Web Server on port 8080...

set JAVA_HOME=C:\Users\i512th\.jdks\temurin-21.0.11
set PATH=%JAVA_HOME%\bin;%PATH%
set MAVEN_CMD="C:\Program Files\JetBrains\IntelliJ IDEA 2026.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd"

if exist target\expensebook-1.0.0.jar (
    echo Launching standalone production JAR...
    java -jar target\expensebook-1.0.0.jar
) else (
    echo Building and launching via Maven...
    call %MAVEN_CMD% package exec:java -Dexec.mainClass="com.expensebook.web.WebApp"
)
pause
