# MarketLens - modo demo. Ejecutar desde la raíz: .\scripts\start-demo.ps1
# PostgreSQL en Docker, backend en Windows (la dockerización completa es otra etapa).
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envPath = Join-Path $root '.env'
$backend = Join-Path $root 'backend'

if (-not (Test-Path -LiteralPath (Join-Path $root 'compose.yaml'))) {
    throw 'Falta compose.yaml en la raíz del proyecto.'
}
if (-not (Test-Path -LiteralPath (Join-Path $backend 'mvnw.cmd'))) {
    throw 'Falta el Maven Wrapper backend/mvnw.cmd.'
}
if (-not (Test-Path -LiteralPath $envPath)) {
    throw 'No existe .env. Copia .env.example y configurá POSTGRES_PASSWORD.'
}

$lines = [System.Collections.Generic.List[string]]::new()
foreach ($line in [System.IO.File]::ReadAllLines($envPath)) { $lines.Add($line) }

$postgresPasswordPresent = $false
foreach ($line in $lines) {
    if ($line -match '^\s*POSTGRES_PASSWORD\s*=\s*(.+)$') {
        $postgresPasswordPresent = $true
    }
}
if (-not $postgresPasswordPresent) {
    throw 'POSTGRES_PASSWORD debe estar definida en el archivo .env.'
}

function Ensure-Secret([string]$name, [int]$bytesCount) {
    $indexes = @()
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match ('^\s*' + [regex]::Escape($name) + '\s*=')) {
            $indexes += $i
        }
    }
    if ($indexes.Count -gt 1) {
        throw ('La variable ' + $name + ' aparece varias veces en .env.')
    }
    if ($indexes.Count -eq 1 -and $lines[$indexes[0]] -match '^\s*[^=]+\s*=\s*(\S.+)$') {
        return
    }

    $bytes = New-Object byte[] $bytesCount
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $value = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')

    $entry = "${name}=$value"
    if ($indexes.Count -eq 1) { $lines[$indexes[0]] = $entry }
    else { $lines.Add($entry) }
    Write-Host "Se generó ${name} para esta instalación (guardado en .env)."
}

Ensure-Secret 'JWT_SECRET' 64
Ensure-Secret 'DEMO_PASSWORD' 24

$utf8 = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllLines($envPath, [string[]]$lines, $utf8)

Push-Location $root
try {
    & docker compose up -d db
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar PostgreSQL en Docker.' }

    Write-Host 'Modo demo: usuario demo; contraseña guardada en .env (DEMO_PASSWORD).'
    Push-Location $backend
    try {
        & .\mvnw.cmd '-Dspring-boot.run.profiles=docker,demo' 'spring-boot:run'
        if ($LASTEXITCODE -ne 0) { throw 'Spring Boot finalizó con error.' }
    } finally { Pop-Location }
} finally { Pop-Location }
