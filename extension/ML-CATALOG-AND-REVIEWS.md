# Mercado Libre: catálogo, ofertas y opiniones del producto (MVP)

## Fichas de catálogo y ofertas individuales

Una URL con `/p/MLA<id>` identifica un PRODUCTO de catálogo. La página puede
contener múltiples ofertas de vendedores con precios diferentes. No se
debe importar ese ID como si perteneciera a una oferta individual.

La extensión puede inspeccionar estas páginas. En la estructura reciente
`#buybox-form`, busca una sola fila `--SELECTED` y contrasta su identificador
MLA con el campo oculto `item_id` del checkout. Obtiene el precio principal y
el vendedor de esa misma fila. **No utiliza el atributo HTML `checked` como
fuente de verdad**, ya que puede permanecer en una opción no seleccionada.

Conservamos la URL ORIGINAL del catálogo y enviamos el identificador separado
`selectedOfferId`. El backend valida que coincide con `externalId` y con
una URL de catálogo de Mercado Libre antes de normalizar la referencia como
`LISTING`. Así no se fabrica una URL individual que quizá no existe.

En versiones antiguas del panel, la extensión mantiene una alternativa que
exige un único enlace de oferta MLA/MLAU, vendedor y precio en el mismo bloque.
Si no hay información consistente, debe rechazar la asociación e indicar
que se abra la oferta individual.

No se atribuyen automáticamente al vendedor seleccionado los datos de
reputación, ventas e insignias del vendedor destacado del catálogo.

Esta detección depende del DOM del sitio: todavía debe probarse con múltiples
selecciones reales en distintas versiones de Firefox, Chrome y Mercado Libre.
No hay acceso a datos internos del navegador ni llamadas a APIs privadas.

## Cobertura de Mercado Libre (V27)

Tres factores independientes:

1. **Información del producto:** descripción almacenada no vacía.
2. **Reputación o actividad del vendedor:** reputación corroborada, termómetro
   visible observado recientemente o actividad comercial observada válida.
3. **Opiniones del producto:** calificación de 0–5 y cantidad exacta y positiva
   de opiniones reconocidas juntas (JSON-LD `Product.aggregateRating` o un
   bloque explícito de reseñas del producto).

La tercera dimensión es *disponibilidad de información*, NO un certificado
de autenticidad. Las opiniones de catálogo pueden pertenecer al producto
compartido entre varios vendedores. Se registran con una fecha de observación
y vencen para la evaluación a los **30 días**. No se sustituye un recuento
de reseñas por ventas, ni se infieren números exactos desde `1.2K+`.

El termómetro visible del vendedor se conserva por separado con su fecha
de observación; no se convierte en reputación corroborada mediante OAuth.

La escala experimental de reputación de Mercado Libre ahora puntúa
**1/5 = +25**, **2/5 = +15**, **3/5 = +8** y **4–5/5 = +0**.
El nivel verificado tiene prioridad sobre el observado y no se duplican
penalizaciones por una observación pública del mismo termómetro.
Una buena puntuación del producto por sí sola nunca permite riesgo bajo
cuando faltan señales del vendedor. En esta etapa las opiniones de Mercado
Libre suman **0 puntos**: antes de aplicar penalizaciones hay que validar
el comportamiento estadístico con un conjunto representativo.

La evaluación nueva devuelve `coverageFactors` con código, nombre y
disponibilidad de cada dimensión para explicar qué falta en 1/3, 2/3 o 3/3.
El detalle por factor de evaluaciones históricas no estaba persistido y
por eso no se inventa retroactivamente.

## Ejecución local y pruebas

Los cambios del backend incluyen
`V27__ml_observed_reputation_and_product_reviews.sql` y
`V28__ml_catalog_selected_offer.sql`; para aplicarlos:

```powershell
docker compose up --build -d
docker compose logs --tail=150 backend
cd backend
.\mvnw.cmd test
cd ..
node --test extension/tests/popup-flow.test.cjs
cd frontend
npm ci
npm run build
```

Después de reconstruir y migrar el backend, recargá la extensión.

Probar una ficha `/p/MLA...` con una única oferta identificable y una
con varias ofertas sin selección inequívoca. Probar un `/up/MLAU...`,
comprobando qué dimensiones figuran como disponibles. Repetir en una
publicación sin descripción, otra sin termómetro y otra con calificación y
recuento de opiniones. La web no garantiza mostrar siempre estos metadatos.
