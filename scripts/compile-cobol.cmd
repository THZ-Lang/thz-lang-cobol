@echo off
setlocal

if "%~1"=="" (
    echo Uso: compile-cobol.cmd ^<arquivo.cbl^> [opcoes cobc]
    exit /b 1
)

cobc -x %*
