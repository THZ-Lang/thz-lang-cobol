@echo off
setlocal

if "%~1"=="" (
    echo Uso: compile-cobol.cmd ^<arquivo.cbl^> [opcoes cobc]
    exit /b 1
)

where cobc >nul 2>nul
if %errorlevel% neq 0 (
    set "COB_MAIN_DIR=%USERPROFILE%\scoop\apps\gnucobol\current\"
    set "COB_CONFIG_DIR=%USERPROFILE%\scoop\apps\gnucobol\current\config"
    set "COB_COPY_DIR=%USERPROFILE%\scoop\apps\gnucobol\current\copy"
    set "COB_CFLAGS=-I"%USERPROFILE%\scoop\apps\gnucobol\current\include""
    set "COB_LDFLAGS=-L"%USERPROFILE%\scoop\apps\gnucobol\current\lib""
    set "COB_LIBRARY_PATH=%USERPROFILE%\scoop\apps\gnucobol\current\extras"
    set "PATH=%USERPROFILE%\scoop\apps\gnucobol\current\bin;%PATH%"
)

cobc -x %*
