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

La sección de reputación `.ui-seller-data` puede aparecer **fuera del
formulario de ofertas**, pero dentro del mismo panel lateral
`.ui-pdp--sticky-wrapper-right`. Después de identificar la fila seleccionada,
MarketLens acepta su termómetro de reputación únicamente si el nombre de la
tienda coincide y un enlace del panel tiene `item_id` igual al MLA elegido.
Esto permite tomar, por ejemplo, la reputación observada **5/5 de PCREGISTRADA**
en la oferta `MLA1995498078`, sin confundirla con la de otras opciones.
Si falta esa corroboración, se deja la reputación como desconocida.

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
   bloque explícito de reseñas del producto). La estructura nueva de Mercado
   Libre `reviews_capability_v3` / `[data-testid="reviews-desktop"]` usa
   `.ui-review-capability__rating__average` para la media y el texto accesible
   `Calificación 5.0 de 5. 2 opiniones.` para el recuento. Si falta el texto
   accesible, se puede usar el contador explícito `2 comentarios` del mismo
   módulo, pero **no** convertir automáticamente `2 calificaciones` en dos
   opiniones. No se toman estrellas de reseñas individuales ni reputación
   de vendedor para completar esta dimensión.

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
cuando faltan señales del vendedor.

### Penalización exploratoria por opiniones del producto

Mercado Libre utiliza señales **independientes y acumulables** para la media
de estrellas y el tamaño de la muestra. Estos pesos todavía deben validarse
con un conjunto representativo; una muestra pequeña refleja incertidumbre,
no demuestra fraude.

| Condición | Puntos |
| --- | ---: |
| 1–4 opiniones observadas | +4 |
| 5–9 opiniones observadas | +2 |
| 10 o más opiniones | +0 por tamaño de muestra |
| Calificación < 3/5, con menos de 10 opiniones | +5 |
| Calificación < 3/5, con 10 o más opiniones | +15 |
| Calificación desde 3/5 hasta < 4/5, con menos de 10 opiniones | +3 |
| Calificación desde 3/5 hasta < 4/5, con 10 o más opiniones | +6 |
| Calificación >= 4/5 | +0 por calificación |

Con 5,0/5 y 2 opiniones, el resultado es **+4 puntos únicamente por la muestra
pequeña**. Con 2,4/5 y 2 opiniones, el resultado es **+9 puntos** (+5 por
calificación desfavorable, +4 por muestra pequeña). Las calificaciones sin
cantidad exacta de opiniones y las observaciones vencidas (más de 30 días)
no generan puntos. Estos datos describen el PRODUCTO de catálogo, no la
calidad comercial del vendedor.

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
