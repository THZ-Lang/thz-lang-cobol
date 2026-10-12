<#
.SYNOPSIS
    Compilador COBOL Nativo de Referencia (GnuCOBOL 3.2) para o ecossistema THZ-LANG.
.DESCRIPTION
    Compila arquivos .cbl/.cob utilizando o GnuCOBOL cobc.
.EXAMPLE
    .\scripts\compile-cobol.ps1 exemplos\programas\CALC_JUROS.cbl
    .\scripts\compile-cobol.ps1 exemplos\programas\CALC_JUROS.cbl -FreeForm -OutFile calc.exe -Run
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$SourceFile,

    [Parameter(Position = 1)]
    [string]$OutFile,

    [switch]$FreeForm,

    [switch]$Run,

    [switch]$Module
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $SourceFile)) {
    Write-Error "Arquivo fonte COBOL nao encontrado: $SourceFile"
}

# Localizar cobc no PATH ou em scoop/apps/gnucobol
$cobcCmd = Get-Command cobc -ErrorAction SilentlyContinue
if (-not $cobcCmd) {
    $fallbackDir = "C:\Users\lucas\scoop\apps\gnucobol\current"
    if (Test-Path "$fallbackDir\bin\cobc.exe") {
        $env:COB_MAIN_DIR = "$fallbackDir\"
        $env:COB_CONFIG_DIR = "$fallbackDir\config"
        $env:COB_COPY_DIR = "$fallbackDir\copy"
        $env:COB_CFLAGS = "-I`"$fallbackDir\include`""
        $env:COB_LDFLAGS = "-L`"$fallbackDir\lib`""
        $env:COB_LIBRARY_PATH = "$fallbackDir\extras"
        $env:PATH = "$fallbackDir\bin;$env:PATH"
    } else {
        Write-Error "Compilador cobc nao encontrado no PATH nem no diretorio de instalacao padrao."
    }
}

$cobcArgs = @()

if ($Module) {
    $cobcArgs += "-m"
} else {
    $cobcArgs += "-x"
}

if ($FreeForm) {
    $cobcArgs += "-free"
}

if ($OutFile) {
    $cobcArgs += @("-o", $OutFile)
}

$cobcArgs += (Resolve-Path $SourceFile).Path

Write-Host "Compilando COBOL: cobc $($cobcArgs -join ' ')" -ForegroundColor Cyan
& cobc @cobcArgs

if ($LASTEXITCODE -ne 0) {
    Write-Error "Falha na compilacao COBOL (exit code $LASTEXITCODE)."
}

Write-Host "Compilacao concluida com sucesso!" -ForegroundColor Green

if ($Run -and -not $Module) {
    $targetExe = if ($OutFile) { $OutFile } else { [System.IO.Path]::ChangeExtension($SourceFile, ".exe") }
    if (Test-Path $targetExe) {
        Write-Host "Executando binario nativo: $targetExe`n" -ForegroundColor Yellow
        & $targetExe
    }
}
