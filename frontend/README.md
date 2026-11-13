# Frontend de MarketLens

Aplicación web de MarketLens, construida con **React, TypeScript y Vite**. Permite iniciar sesión, consultar el panel de estadísticas, administrar publicaciones y vendedores y solicitar evaluaciones de riesgo.

> Para instalar y ejecutar **todo el sistema** (frontend, backend y PostgreSQL), consultá el [README principal](../README.md). La forma recomendada de iniciar MarketLens es mediante Docker Compose desde la raíz del proyecto.

## Registro e inicio de sesión

Iniciá los tres servicios según el [README principal](../README.md), preferentemente mediante `scripts/start-compose.ps1` en Windows. Después:

1. Abrí **http://localhost:5173/login**.
2. Seleccioná **Crear cuenta** (ruta `/register`).
3. Ingresá nombre de usuario, correo y contraseña; confirmá la contraseña.
4. Al completar el registro, el sistema vuelve al inicio de sesión.
5. Ingresá con tu nuevo usuario y la contraseña elegida.

El endpoint `POST /api/auth/register` ya está implementado en el backend. El modo `registration` no crea la cuenta `demo`, pero carga publicaciones ficticias.
Si preferís la cuenta predefinida, iniciá `scripts/start-compose-demo.ps1` y consultá `DEMO_PASSWORD` en el archivo `.env` privado.

Las secciones disponibles incluyen:

- **Dashboard:** resumen de publicaciones, vendedores, evaluaciones y reportes.
- **Vendedores:** consulta y registro de vendedores.
- **Publicaciones:** consulta, registro y evaluación de publicaciones; desde el detalle se pueden ver las evaluaciones y los reportes.
- **Acceso desde la extensión:** la ruta `/auth/extension` recibe un código temporal de un solo uso generado por el backend cuando la extensión abre una publicación en MarketLens.

Los resultados de riesgo son **indicativos**, dependen de los datos disponibles y no certifican que una operación sea segura o fraudulenta.

## Desarrollo local del frontend

Requisitos para esta modalidad: **Node.js y npm**, más el backend de MarketLens ejecutándose en `http://localhost:8080`.

Desde la carpeta `frontend/`:

```bash
npm ci
npm run dev
```

Vite sirve el frontend y redirige las peticiones a `/api` hacia `http://localhost:8080`, de acuerdo con `vite.config.ts`. El cliente HTTP utiliza rutas relativas `/api`, así que no es necesario escribir una URL absoluta del backend en el código.

En la ejecución con Docker, Nginx sirve los archivos compilados y hace el proxy de `/api` al servicio `backend` de Compose. No confundas este modo con el servidor de desarrollo de Vite.

## Comandos disponibles

Desde `frontend/`:

```bash
npm ci           # instalar las dependencias fijadas en package-lock.json
npm run dev      # servidor de desarrollo
npm run build    # comprobar TypeScript y generar dist/
npm run lint     # ejecutar ESLint
```

La carpeta `dist/` es generada y no se incluye en Git. Tampoco se deben versionar `node_modules/` ni archivos locales `.env` con credenciales.

## Código y rutas relevantes

- `src/App.tsx`: navegación, sesión, páginas y tema visual.
- `src/api/client.ts`: peticiones al backend mediante `/api`.
- `src/auth/session.ts`: almacenamiento y vencimiento de la sesión.
- `src/components/`: paneles, formularios y detalles de publicación.
- `vite.config.ts`: proxy de desarrollo.
- `nginx.conf`: frontend estático y proxy del backend en Docker.
- `Dockerfile`: compilación de producción para la demostración.

**Importante:** este frontend forma parte de un prototipo académico local. La configuración del repositorio no constituye un despliegue público de producción.
