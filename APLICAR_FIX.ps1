# Corrección mínima de la ruta de acceso desde la extensión de MarketLens.
# Ejecutar desde la raíz del proyecto (donde está compose.yaml).
# No cambia la base de datos ni las credenciales; V19 la ejecuta Flyway.
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$appPath = Join-Path $root 'frontend/src/App.tsx'
$loginPath = Join-Path $root 'frontend/src/components/ExtensionLoginPage.tsx'
$migrationPath = Join-Path $root 'backend/src/main/resources/db/migration/V19__allow_indeterminate_risk_level.sql'

foreach ($path in @($appPath, $loginPath, $migrationPath)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Falta un archivo necesario: $path" }
}

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$appOriginal = [System.IO.File]::ReadAllText($appPath)
$loginOriginal = [System.IO.File]::ReadAllText($loginPath)
$appEol = if ($appOriginal.Contains("`r`n")) { "`r`n" } else { "`n" }
$loginEol = if ($loginOriginal.Contains("`r`n")) { "`r`n" } else { "`n" }
$app = $appOriginal.Replace("`r`n", "`n")
$login = $loginOriginal.Replace("`r`n", "`n")

$oldRoute = @'
          <Route
            path="/auth/extension"
            element={
              token ? (
                <Navigate
                  to={getPublicationPath(new URLSearchParams(location.hash.slice(1)).toString()) ?? '/dashboard'}
                  replace
                />
              ) : (
                <ExtensionLoginPage onSession={acceptExtensionSession} />
              )
            }
          />
'@
$newRoute = @'
          <Route
            path="/auth/extension"
            element={<ExtensionLoginPage onSession={acceptExtensionSession} />}
          />
'@

$oldFetch = "fetch('http://localhost:8080/api/auth/extension-exchange', {"
$newFetch = "fetch('/api/auth/extension-exchange', {"

# Abortamos sin escribir nada si la versión del repositorio no coincide.
if (-not $app.Contains($oldRoute)) {
    throw 'No se encontró la ruta original en App.tsx. Revisá los cambios manualmente.'
}
if (-not $login.Contains($oldFetch)) {
    throw 'No se encontró el fetch original en ExtensionLoginPage.tsx. Revisá los cambios manualmente.'
}

$app = $app.Replace($oldRoute, $newRoute).Replace("`n", $appEol)
$login = $login.Replace($oldFetch, $newFetch).Replace("`n", $loginEol)

[System.IO.File]::WriteAllText($appPath, $app, $utf8NoBom)
[System.IO.File]::WriteAllText($loginPath, $login, $utf8NoBom)
Write-Host 'Listo: corregida la ruta /auth/extension en App.tsx.'
Write-Host 'Listo: el intercambio de sesión utiliza el proxy local /api.'
Write-Host 'Listo: V19 incluida para que Flyway amplíe los niveles de riesgo.'
Write-Host 'Siguiente paso: docker compose up --build -d'
