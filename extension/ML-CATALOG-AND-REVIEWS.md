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

En fichas de catálogo que NO presentan selector lateral de ofertas, Mercado
Libre puede mostrar un único formulario `#buybox-form` con `item_id` de
oferta MLA, `parent_url` del catálogo y un vendedor asociado. En ese caso
se acepta la oferta **solo si** el ID del catálogo coincide con
`parent_url`, existe exactamente un `item_id`, hay un vendedor en el
formulario y el importe principal único coincide con sus metadatos de precio
en ARS. Ejemplo verificado con HTML: catálogo SanDisk `MLA2085086111`,
oferta `MLA4003237102`. Si faltan pruebas o aparecen varios IDs, rechazar.

**Varios selectores simultáneos:** algunos catálogos de celulares, además de
«Excelente/Bueno/Aceptable», presentan un selector de color independiente.
No debe exigirse un único botón `--SELECTED` en toda la ficha: cada grupo
`.ui-pdp-outside_variations__thumbnails` debe tener exactamente una
selección y **todas** las selecciones deben enlazar al mismo catálogo
`/p/MLA...` abierto. Se rechazan grupos ambiguos y combinaciones donde
color y estado conducen a productos distintos.

Ejemplo de regresión: Galaxy A13 Negro 128 GB Excelente, catálogo
`MLA2000090922`, oferta `MLA4004994290`, vendedor `DDOM INSUMOS`,
precio `349999 ARS`. El HTML contiene dos grupos seleccionados (Black y
Excelente), ambos del mismo catálogo; su JSON-LD declara 4,5 estrellas
y 59 reseñas. Son **dos atributos de una oferta**, no dos ofertas.

**Catálogos reacondicionados con selector de estado:** las opciones
«Excelente», «Bueno» y «Aceptable» pueden referirse a **fichas de
catálogo distintas**. Si aparece
`.ui-pdp-outside_variations__thumbnails__item`, la extensión exige una
única variante `--SELECTED` cuya URL corresponda al catálogo abierto.
Las variantes `--BLOCKED` no deben considerarse ofertas disponibles.
La oferta concreta se identifica mediante el único `item_id` del
`#buybox-form`, corroborado con `parent_url` y el vendedor dentro
del formulario.

En ciertas fichas, **el precio principal está fuera de
`#buybox-form`**: se busca el único monto de
`.ui-pdp-price__second-line` con `meta[itemprop="price"]` fuera de
carruseles, y se compara el valor visible con el dato estructurado.
Si aparecen dos montos principales, varios `item_id` o una variante
seleccionada que corresponde a otro catálogo, se rechaza la importación.

Ejemplo comprobado en HTML: Samsung Galaxy A33 reacondicionado, catálogo
`MLA2000136014` («Excelente»), oferta `MLA2270730739`,
vendedor `OUTLETIT383`, precio `319999 ARS`. «Bueno» y «Aceptable»
son catálogos separados actualmente bloqueados. La condición del
producto se conserva en el título de la publicación; todavía corresponde
verificarla como atributo explícito antes de utilizar referencias en una
comparación de precios entre equipos reacondicionados.

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

### Aislamiento de reseñas y calificaciones no disponibles (V30)

- Las tarjetas recomendadas (incluidos los carruseles patrocinados) no
  aportan calificaciones al producto analizado. También se comprueba que
  cualquier `Product` en JSON-LD pertenezca a la URL o título principal,
  y no se acepta automáticamente el primer objeto del documento.
- **La visibilidad en pantalla NO es requisito.** Un `aggregateRating`
  con calificación y `reviewCount` válido del JSON-LD del producto
  correcto sí cuenta para la cobertura y el análisis, aunque la sección
  visual de reseñas no exista.
- Ejemplo real: Kingston `MLAU2874874181`, cuyo JSON-LD declara
  `ratingValue: 4.9` y `reviewCount: 1552`. Se aceptan ambos valores
  aunque no haya módulo visual de reseñas. No se deben confundir con el
  4,9 de productos promocionados.
- `mlProductRatingUnavailable=true` solo se registra si la página está
  cargada, se observan las preguntas, **y no hay calificación/recuento
  válidos ni en el producto estructurado ni en sus módulos de reseñas**.
  Esto indica ausencia de evidencia disponible, no cero reseñas.
- Si existe información parcial o la extracción fue inconclusa, se
  mantiene el estado desconocido (null), sin puntos. La ausencia
  confirmada suma **+2 puntos**, no completa cobertura y vence a 30 días.
  Una nueva observación válida reemplaza la ausencia anterior.
- Si una inspección concluyente registra ausencia, puede limpiar datos
  antiguos mal atribuidos; las evaluaciones históricas no se reescriben.

Migración: `V30__ml_public_rating_visibility.sql`.

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
| Puntuación pública no visible, observación concluyente | +2 (sin cobertura adicional) |
| Datos desconocidos o lectura incompleta | +0 |
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

Las descripciones de las señales de producto en Mercado Libre se mantienen
breves e independientes: `Calificación del producto: 5,0/5.` y
`Cantidad de opiniones reducida: 2.` El frontend omite los códigos internos
y muestra las señales de cero puntos como informativas, sin `+0`.
Para el historial antiguo adapta las descripciones largas solo al mostrarlas;
no modifica las evaluaciones ya persistidas.

La evaluación nueva devuelve `coverageFactors` con código, nombre y
disponibilidad de cada dimensión para explicar qué falta en 1/3, 2/3 o 3/3.
El detalle por factor de evaluaciones históricas no estaba persistido y
por eso no se inventa retroactivamente.

## URLs limpias y parámetros de seguimiento (extensión)

La extensión normaliza las URLs de las publicaciones de Mercado Libre
antes de guardarlas e importarlas: conserva `https://host/ruta-del-producto`
y descarta query strings y fragmentos, como `pdp_filters`,
`searchVariation`, `is_advertising` y `tracking_id`. Estos parámetros
pueden modificar la presentación u oferta observada: por eso la extensión
extrae los datos de la ficha abierta antes de eliminar la query de la URL
persistida.
El ID de la publicación `MLAU...` o `MLA...` se obtiene de la ruta.
Para una oferta seleccionada en una ficha `/p/MLA...`, el
`selectedOfferId` se mantiene **como campo separado**, nunca se
sustituye por el ID del catálogo.

Se realiza otra normalización defensiva justo al construir el
`POST /extension/imports`. La clave de caché del popup para ML también
usa la URL sin seguimiento. Temu y Facebook no reciben este tratamiento
porque sus parámetros pueden ser funcionales (por ejemplo, `goods_id`).

Esto evita persistir enlaces publicitarios como URL de origen; no
necesariamente corrige cualquier otro rechazo HTTP 400 causado por
validaciones distintas del backend.

## Ejecución local y pruebas

Los cambios del backend incluyen
`V27__ml_observed_reputation_and_product_reviews.sql` y
`V28__ml_catalog_selected_offer.sql` y
`V30__ml_public_rating_visibility.sql`; para aplicarlos:

```powershell
docker compose up --build -d
docker compose logs --tail=150 backend
cd backend
.\mvnw.cmd test
cd ..
node --test extension/tests/*.test.cjs
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
