@echo off
setlocal
pushd "%~dp0"
if defined EZCD_GRADLE goto configured
where gradle >nul 2>nul
if errorlevel 1 goto missing
call gradle --offline :app:assembleDebug %*
set "BUILD_RESULT=%errorlevel%"
goto done
:configured
call "%EZCD_GRADLE%" --offline :app:assembleDebug %*
set "BUILD_RESULT=%errorlevel%"
goto done
:missing
echo No local Gradle command found. Set EZCD_GRADLE to your existing gradle.bat.
echo Example: set "EZCD_GRADLE=D:\Gradle\gradle-9.6.1\bin\gradle.bat"
echo This script will not download a Gradle distribution.
set "BUILD_RESULT=1"
:done
if not "%BUILD_RESULT%"=="0" echo Offline build failed. Read README.md before allowing downloads.
popd
exit /b %BUILD_RESULT%
