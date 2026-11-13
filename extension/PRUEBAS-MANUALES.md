# Verificación manual de la extensión MarketLens

Esta guía sirve para comprobar el funcionamiento de la **extensión experimental** junto con la instalación local de MarketLens. Es una lista de pruebas, no un resultado de ejecución: debe completarse en un navegador real.

## 1. Preparar los servicios

En la raíz del repositorio, ejecutá el script de inicio que figura en el README principal:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-compose-demo.ps1
```

Comprobá los contenedores:

```powershell
docker compose ps
```

Esperado: `db`, `backend` y `frontend` activos, con `db` saludable. El frontend debe responder en **http://localhost:5173**; el backend, en **http://localhost:8080**. Un `401` en una ruta protegida sin JWT es normal.

La extensión habla con `http://localhost:8080/api` **directamente**, no con el proxy de Nginx. Mantené el backend encendido.

## 2. Instalar temporalmente la extensión en Firefox

1. Abrí `about:debugging#/runtime/this-firefox`.
2. Seleccioná **Cargar complemento temporal**.
3. Elegí el archivo `extension/manifest.json` de este mismo repositorio.
4. Buscá **MarketLens** entre las extensiones temporales. Si necesitás depurarla, usá **Inspeccionar** desde esa pantalla.
5. Si modificás la extensión, hacé clic en **Recargar** desde `about:debugging`.

Esta instalación es temporal: Firefox la elimina al reiniciarse. La estructura `background.scripts` y `background.service_worker` del manifiesto permite que Firefox y Chrome usen sus respectivos sistemas de ejecución en segundo plano en versiones actuales. Aun así, el comportamiento real debe probarse en cada navegador.

Chrome/Chromium, como alternativa: abrí `chrome://extensions`, activá **Modo desarrollador**, presioná **Cargar descomprimida** y seleccioná la carpeta `extension/`.

## 3. Lista de verificaciones

Anotá **Aprobado**, **Falló** o **No probado** para cada caso.

| Caso | Procedimiento | Resultado esperado | Estado |
| --- | --- | --- | --- |
| 1. Inicio | Abrir el popup de MarketLens en una pestaña cualquiera | Muestra el formulario de acceso o el usuario actual, sin quedar en blanco | Pendiente |
| 2. Login | Iniciar sesión con `demo` y el valor local de `DEMO_PASSWORD` | Muestra el nombre del usuario y habilita las funciones donde corresponde | Pendiente |
| 3. Página no compatible | Abrir una pestaña que no sea una publicación individual compatible | Informa que se debe abrir una publicación y no permite analizarla | Pendiente |
| 4. Extracción | Abrir una **publicación individual** de Mercado Libre o Facebook Marketplace y pulsar el icono de la extensión | Detecta el marketplace y reconoce que la página es compatible | Pendiente |
| 5. Análisis | Pulsar **Analizar publicación** | Muestra progreso y luego un resultado de riesgo o un mensaje de error explicativo; nunca queda bloqueado indefinidamente | Pendiente |
| 6. Resultado | Si el análisis termina correctamente, revisar el detalle | Muestra nivel/puntaje/cobertura según los datos disponibles; la cobertura parcial no implica seguridad | Pendiente |
| 7. Abrir frontend | Pulsar **Ver en MarketLens** después de un análisis correcto | Abre el detalle correspondiente en `localhost:5173`; normalmente transfiere la sesión mediante un código temporal | Pendiente |
| 8. Reevaluación | Volver a analizar la misma publicación | Puede crear una nueva evaluación, pero no debería duplicar la publicación para el mismo identificador externo | Pendiente |
| 9. Reporte manual | **Solo en la base local de pruebas**: utilizar una publicación destinada a prueba y el formulario de reporte | Confirma que se registró el reporte y devuelve el identificador de publicación | Pendiente |
| 10. Cierre de sesión | Pulsar **Cerrar sesión** en la extensión | Oculta acciones autenticadas y muestra nuevamente el formulario de acceso | Pendiente |

**Nota sobre reportes:** no registres una denuncia ficticia contra un vendedor real como si fuera una sospecha auténtica. Si probás la persistencia del formulario, utilizá un entorno de prueba y señalá expresamente que se trata de una verificación técnica. El botón de reporte escribe datos en la base local.

### Qué revisar en cada caso

**Extracción.** El sistema reconoce principalmente URLs individuales con identificadores `MLA`/`MLAU` de Mercado Libre o páginas `/marketplace/item/{id}` de Facebook. La estructura de las páginas de terceros puede cambiar. En Facebook, si faltan datos esenciales, la extensión puede ofrecer un formulario para completarlos manualmente.

**Análisis.** El popup envía el trabajo a `background.js`, que importa o vincula la publicación, registra observaciones visibles de actividad del vendedor (cuando corresponde) y solicita la evaluación. El resultado puede carecer de ciertos factores; esto no debe interpretarse como riesgo cero.

**Ver en MarketLens.** La extensión solicita un código temporal a `/api/auth/extension-link` y el frontend lo canjea con `/api/auth/extension-exchange`. El JWT **no se coloca en la URL**. Si la transferencia no funciona, la extensión puede abrir la página para iniciar sesión manualmente; anotá la diferencia.

**Reportes.** Probá esta operación únicamente si necesitás validar el formulario en la base de prueba; no es requisito crear reportes artificiales para mostrar el análisis de riesgo.

## 4. Diagnóstico ante errores

| Síntoma | Comprobación |
| --- | --- |
| No carga el complemento | Revisar los errores del manifiesto en `about:debugging`; el archivo debe ser el `manifest.json` de `extension/`. |
| «No se pudo conectar con el backend» | Consultar `docker compose ps` y `docker compose logs --tail=150 backend`; verificar que el puerto local 8080 esté accesible. |
| «Tu sesión expiró» o HTTP 401 | Iniciar sesión nuevamente desde el popup. El JWT tiene una duración configurada por el backend. |
| La pestaña aparece como no compatible | Verificar la URL individual de publicación, no la de una búsqueda o listado genérico. |
| Faltan título, precio o vendedor | Revisar la extracción o el formulario de completado disponible. Los sitios externos pueden haber cambiado. |
| Un análisis queda en error | Consultar el mensaje del popup y los registros de Spring Boot. La falta de datos no siempre se puede resolver automáticamente. |
| «Ver en MarketLens» pide iniciar sesión | La transferencia automática pudo fallar o expirar; registrar el caso y revisar los logs sin publicar tokens. |
| OAuth de Mercado Libre no funciona | Es esperado en el perfil `demo`; no bloquea por sí solo las funciones basadas en datos visibles. |

Comando de diagnóstico:

```powershell
docker compose logs --tail=150 backend
```

Para problemas de la extensión, también podés usar **Inspeccionar** desde `about:debugging`. Evitá compartir JWT, contraseñas o códigos temporales de acceso en capturas o registros.

## 5. Resultado de la prueba

Al concluir, completá en tu copia de esta guía:

- Navegador y versión:
- Fecha:
- Casos aprobados / fallidos / no probados:
- Error reproducible, si lo hubiera (sin datos sensibles):
- Confirmación final: **Extensión probada** / **Extensión parcialmente probada** / **Extensión pendiente**.

Esta verificación manual complementa los tests automáticos del backend y la prueba de instalación limpia con Docker. **No constituye una auditoría integral de seguridad.**
