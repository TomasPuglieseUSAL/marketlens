# Prueba acotada de OAuth centralizado para reseñas públicas

**Estado: experimental.** Solo pretende validar acceso a
`GET /reviews/item/{ITEM_ID}` de publicaciones públicas de *diferentes*
vendedores usando UNA sola cuenta autorizada por quien opera MarketLens.
No es un sistema de tokens de producción.

## Qué cambia y qué NO

- El perfil `connected` habilita OAuth si el operador lo configura.
- La extensión y los usuarios normales de MarketLens **no necesitan**
  conectar sus cuentas de Mercado Libre. El backend usa la autorización
  de la cuenta MarketLens indicada en `MELI_REVIEWS_CENTRAL_OWNER`.
- Los demás endpoints de API que requieren cuentas individuales
  conservan sus reglas actuales: **el token central se utiliza
  exclusivamente para reseñas públicas**.
- Los perfiles `demo` y `registration` mantienen OAuth deshabilitado.
- El token sigue **en memoria** y caduca. Reiniciar el backend lo elimina.
  **Solo si esta prueba funciona** corresponde implementar cifrado,
  persistencia en PostgreSQL y rotación coordinada del refresh token.

## Preparativos

1. Tener una aplicación propia para **Mercado Libre** en el DevCenter,
   con permisos de solo lectura y, si se desea renovar después,
   `offline_access`. No publicar `Client Secret` ni tokens.
2. Configurar en DevCenter una **Redirect URI HTTPS** fija, con su ruta
   exacta `/api/integrations/mercadolibre/callback`. Para un backend
   local se requiere un túnel HTTPS que reenvíe esa dirección al
   `localhost:8080` donde corre el backend. La URL debe ser idéntica
   a la registrada; no añadir parámetros variables.
3. Tener una cuenta **existente de MarketLens** que operará la prueba.
   Se usará solo para iniciar la autorización en nuestro backend.
   La cuenta que conceda permisos en Mercado Libre debe estar
   autorizada legítimamente para hacerlo.

Completar en `.env` (solo valores propios, no copiar secretos al chat):

```dotenv
MARKETLENS_PROFILE=connected
MELI_CLIENT_ID=APP_ID_REAL
MELI_CLIENT_SECRET=SECRET_REAL
MELI_REDIRECT_URI=https://TU_HOST_SEGURO/api/integrations/mercadolibre/callback
MELI_PKCE_ENABLED=true
MELI_REVIEWS_CENTRAL_ENABLED=true
MELI_REVIEWS_CENTRAL_OWNER=NOMBRE_USUARIO_MARKETLENS_OPERADOR
```

**Importante**: `compose.yaml` inyecta las variables opcionales al
backend. Con OAuth habilitado, `MELI_REDIRECT_URI` debe ser HTTPS.

## Comprobación en local

Desde la raíz:

```powershell
git pull origin fix/ml-catalog-seller-button
cd backend
.\mvnw.cmd clean test
cd ..
node --test extension/tests/*.test.cjs
docker compose up --build -d
```

Iniciar sesión como `MELI_REVIEWS_CENTRAL_OWNER`. Se puede hacer
desde Postman o PowerShell; el ejemplo usa variables de sesión:

```powershell
$usuario = Read-Host "Usuario operador de MarketLens"
$clave = Read-Host "Clave de MarketLens" -AsSecureString
# Convertir solo para construir la petición HTTP local, nunca imprimirla.
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($clave)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $body = @{ username = $usuario; password = $password } | ConvertTo-Json
    $login = Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/auth/login -ContentType application/json -Body $body
} finally {
    if ($ptr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    Remove-Variable password,body -ErrorAction SilentlyContinue
}
$headers = @{ Authorization = "Bearer $($login.token)" }
$link = Invoke-RestMethod -Method POST -Uri http://localhost:8080/api/integrations/mercadolibre/authorize -Headers $headers
$link.authorizationUrl
```

**Abrir** la URL devuelta en el navegador. Autorizar desde la cuenta
de Mercado Libre que gestionará la integración. Tras el callback
HTTPS, el backend habrá recibido el token de forma privada.

Comprobar:

```powershell
Invoke-RestMethod -Uri http://localhost:8080/api/integrations/mercadolibre/central-reviews/status -Headers $headers
```

El operador debería obtener `{"connected":true}` si sigue vigente.
Solo esta cuenta MarketLens puede usar `/status` y `/probe`.
Los consumidores de la extensión **no** pueden usar estos diagnósticos.

## Prueba de la hipótesis principal

Seleccionar **dos IDs reales `MLA...`**, activos, de vendedores
distintos, con opiniones propias. No usar un `MLAU` de catálogo.
Idealmente consultar también una publicación sin opiniones de ítem.

```powershell
$ids = @('MLA_ID_REAL_1', 'MLA_ID_REAL_2')
foreach ($id in $ids) {
    try {
        Invoke-RestMethod -Uri "http://localhost:8080/api/integrations/mercadolibre/central-reviews/probe/$id" -Headers $headers
    } catch {
        Write-Host "Consulta $id: HTTP $([int]$_.Exception.Response.StatusCode)"
    }
}
```

Interpretación:

- **200 / ITEM_VERIFIED**: la API respondió y el ejemplo contiene
  reseñas publicadas asociadas explícitamente a ese `item_id`.
- **200 / NOT_ITEM_VERIFIED**: la consulta fue aceptada, pero las
  muestras no permiten corroborar que la valoración corresponda
  exclusivamente a ese ítem; esto NO implica falta de permisos.
- **401 / 403**: revisar validez del token y permisos. Si sucede
  en publicaciones ajenas con token vigente, podría invalidar
  la hipótesis de acceso centralizado.
- **404**: no se encontró el ítem o el recurso; no demuestra que
  falle OAuth. **429**: hay limitación de consultas.

Si el token de una sola cuenta permite leer ítems de vendedores
diferentes y se obtienen respuestas satisfactorias, la hipótesis
queda respaldada. Todavía habrá que garantizar la rotación segura de
credenciales y el cumplimiento de las condiciones de uso.

## Reglas de seguridad

- No introducir tokens de Mercado Libre en el popup ni enviarlos
  a los usuarios finales; solo los almacena el backend.
- No subir el archivo `.env`, imprimir tokens en logs ni pegarlos
  en chats/capturas.
- Evitar crear autorizaciones duplicadas desde muchas sesiones.
- Las reseñas compartidas del catálogo permanecen como contexto,
  fuera de la cobertura de confiabilidad de la oferta.
- La API puede rechazar, revocar o limitar el acceso; no se promete
  una sesión permanente.
