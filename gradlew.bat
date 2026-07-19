@echo off
setlocal
set GRADLE_VERSION=8.14.3
set WRAPPER_DIR=%~dp0.gradle\wrapper-local
set GRADLE_HOME=%WRAPPER_DIR%\gradle-%GRADLE_VERSION%
set GRADLE_BIN=%GRADLE_HOME%\bin\gradle.bat

if not exist "%GRADLE_BIN%" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $v='%GRADLE_VERSION%'; $dir='%WRAPPER_DIR%'; New-Item -ItemType Directory -Force -Path $dir | Out-Null; $zip=Join-Path $dir ('gradle-' + $v + '-bin.zip'); if (!(Test-Path $zip)) { Invoke-WebRequest -Uri ('https://services.gradle.org/distributions/gradle-' + $v + '-bin.zip') -OutFile $zip }; Expand-Archive -LiteralPath $zip -DestinationPath $dir -Force"
  if errorlevel 1 exit /b %errorlevel%
)

call "%GRADLE_BIN%" %*
