# MarketLens: inicia los TRES servicios en Docker con registro de usuarios (sin demo).
# Ejecucion: powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-compose.ps1
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envPath = Join-Path $root '.env'
if (-not (Test-Path (Join-Path $root 'compose.yaml'))) { throw 'No existe compose.yaml en raiz.' }
if (-not (Test-Path (Join-Path $root 'backend/Dockerfile'))) { throw 'Falta backend/Dockerfile.' }
if (-not (Test-Path (Join-Path $root 'frontend/Dockerfile'))) { throw 'Falta frontend/Dockerfile.' }
if (-not (Test-Path $envPath)) { throw 'No existe .env. Copia .env.example y configura POSTGRES_PASSWORD.' }

$lines = [System.Collections.Generic.List[string]]::new()
foreach ($line in [System.IO.File]::ReadAllLines($envPath)) { $lines.Add($line) }

function SecretValue([string]$name) {
    $entries = @($lines | Where-Object { $_ -match ('^\s*' + [regex]::Escape($name) + '\s*=') })
    if ($entries.Count -gt 1) { throw "$name aparece mas de una vez en .env" }
    if ($entries.Count -eq 0) { return '' }
    return ($entries[0] -replace ('^\s*' + [regex]::Escape($name) + '\s*=\s*'), '').Trim()
}

if (-not (SecretValue 'POSTGRES_PASSWORD')) { throw 'Falta POSTGRES_PASSWORD en .env' }

function Ensure-Secret([string]$name, [int]$n) {
    if (SecretValue $name) { return }
    $buf = New-Object byte[] $n
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($buf) } finally { $rng.Dispose() }
    $val = [Convert]::ToBase64String($buf).TrimEnd('=').Replace('+','-').Replace('/','_')
    $entry = "${name}=$val"
    $found = $false
    for ($i=0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match ('^\s*' + [regex]::Escape($name) + '\s*=')) {
            $lines[$i] = $entry
            $found = $true
            break
        }
    }
    if (-not $found) { $lines.Add($entry) }
    Write-Host "Se creo $name en .env para esta instalacion."
}
Ensure-Secret 'JWT_SECRET' 64
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllLines($envPath, [string[]]$lines, $utf8NoBom)

# Este script utiliza el perfil registration; no crea usuario demo.
$oldProfile = $env:MARKETLENS_PROFILE
$env:MARKETLENS_PROFILE = 'registration'
Push-Location $root
try {
    Write-Host 'Construyendo e iniciando PostgreSQL + backend + frontend...'
    & docker compose up --build -d
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose no pudo iniciar los servicios.' }
    & docker compose ps
    if ($LASTEXITCODE -ne 0) { throw 'No fue posible consultar los servicios.' }
    Write-Host 'Frontend: http://localhost:5173'
    Write-Host 'Backend:  http://localhost:8080'
    Write-Host 'Abri http://localhost:5173/register para crear una cuenta personal.'
    Write-Host 'Si no responde aun, ver los logs: docker compose logs -f backend'
} finally {
    Pop-Location
    if ($null -eq $oldProfile) {
        Remove-Item Env:MARKETLENS_PROFILE -ErrorAction SilentlyContinue
    } else {
        $env:MARKETLENS_PROFILE = $oldProfile
    }
}
