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

**Variante recién cambiada con metadatos desactualizados:** al pasar
entre combinaciones de color, almacenamiento y condición, Mercado Libre
puede conservar un `parent_url` oculto o JSON-LD de la variante anterior,
aunque los selectores y el precio principal ya hayan cambiado. En este
caso NO se utiliza el JSON-LD de otro catálogo para obtener reseñas.

Para resolver la identidad individual con seguridad, la extensión permite
que `parent_url` difiera de la ruta actual **únicamente** si existe un
enlace independiente `a.ui-pdp-syi__link` («Vender uno igual») que
corrobore simultáneamente `itemId` del checkout y `productId` del
catálogo actual, y además se validan todos los grupos seleccionados,
el vendedor y el precio. Se rechaza si el enlace no existe, no coincide
o está duplicado. Esto mantiene la protección contra la mezcla accidental
de ofertas de distintas variantes.

Ejemplo comprobado: Galaxy A13 64 GB Celeste Aceptable,
catálogo `MLA2000102466`, oferta `MLA2282119419`, vendedor
`SANTIJULIANN_`, precio `239995 ARS`. Su formulario conserva
`parent_url=/p/MLA2000090922` y su JSON-LD describe el Galaxy A13
negro. El enlace de venta asociada sí vincula la oferta con
`MLA2000102466`, y permite importar sin atribuirle las reseñas
de la variante anterior.

**Catálogo de oferta única con vendedor mostrado como botón:** Mercado Libre
puede presentar al vendedor dentro de `#buybox-form` en
`.ui-pdp-seller-summary__label-text-no-action`, en lugar de usar el enlace
`.ui-pdp-seller-summary__link`. Se permite cualquiera de esas formas,
pero se exige **un solo vendedor** dentro del formulario actual, además
de un único `item_id`, `parent_url` corroborado y precio principal
coincidente. Los vendedores de recomendaciones no se aceptan como
identidad de la oferta.

Caso de regresión: Galaxy A13 64 GB Negro «Nuevo con caja abierta»,
catálogo `MLA2015816941`, oferta `MLA2629481668`, vendedor
`LOTARINO`, precio `250000 ARS` y sin selectores de variantes.
Como su condición no es «usado», **no debería emplearse como referencia
automática del Galaxy A13 usado** de $2.000 sin equivalencia comercial.

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
- **La visibilidad en pantalla NO es requisito para registrar el contexto.**
  Un `aggregateRating` con calificación y `reviewCount` válido del JSON-LD
  del producto correcto puede informarse, aunque la sección visual de
  reseñas no exista. Nunca cuenta para el puntaje ni la cobertura de
  confiabilidad de la oferta individual.
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
  confirmada también es neutral: no prueba fraude, no completa cobertura
  y vence a 30 días. Una nueva observación válida reemplaza la anterior.
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

### Reseñas compartidas: información del modelo, no riesgo de la oferta

Las opiniones generales de un producto de Mercado Libre pueden reunir
compras a vendedores diferentes. Por ello **todas sus señales pasan a 0
puntos**, independientemente de la media, cantidad de reseñas o ausencia
concluyente. Se mantienen para informar sobre la calidad percibida del
modelo; no constituyen evidencia sobre la reputación del vendedor.

Se conservan los códigos históricos `ML_PRODUCT_REVIEWS_OBSERVED`,
`ML_LOW_PRODUCT_RATING`, `ML_MODERATE_PRODUCT_RATING`,
`ML_FEW_PRODUCT_REVIEWS` y `ML_PRODUCT_RATING_UNAVAILABLE`,
pero su contribución es **0** en las nuevas evaluaciones.
La interfaz declara expresamente que estas calificaciones no verifican
la oferta. Las evaluaciones históricas no se reescriben automáticamente.

La cobertura básica de Mercado Libre comprende descripción e
información comercial del vendedor; las opiniones del MODELO quedan
fuera. En ítems MLA individuales elegibles se agrega condicionalmente
un tercer factor de **opiniones propias del ítem**, verificado por la API
de Mercado Libre. Temu conserva sus factores propios. Los indicadores
son disponibilidad de datos, no pruebas de seguridad.

### Precio anormal: tramos y referencias

Se necesita un mínimo de **cinco ofertas independientes comparables**,
ya filtradas por el algoritmo de matching. Las diferencias se miden
contra la mediana en ARS, no contra un precio fijo o una marca.

| Precio como porcentaje de la mediana | Puntos |
| --- | ---: |
| 70 % o más | 0 |
| 60 % a menos de 70 % | +5 |
| 50 % a menos de 60 % | +15 |
| 25 % a menos de 50 % | +25 |
| 10 % a menos de 25 % | +35 |
| Menos de 10 % | +45 |

Caso ilustrativo: Galaxy A13 usado a `2000 ARS`, mediana de
`100000 ARS` calculada sobre cinco o más ofertas realmente comparables.
Por precio: **+45**; por historial del vendedor insuficiente: **+15**;
total esperado **60/100 (riesgo alto)**, si no existen otros factores.
La calificación general del Galaxy A13, aunque figure como 4,7/5,
no suma ni resta puntos. La etiqueta de la extensión destaca el precio
«extremadamente inferior» y señala que las reseñas no verifican la
oferta particular.

Los umbrales son **heurísticos experimentales**, no probabilidades
estadísticas de fraude. Se deberá validarlos con datos etiquetados antes
de usarlos como criterio automatizado concluyente.

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

## Alcance de reseñas y diagnóstico de errores 422

La calificación JSON-LD de Mercado Libre es **general del producto**:
puede reunir experiencias de distintos vendedores. La extensión puede
extraerla aunque no esté visible en la ficha, pero una valoración alta
**no reduce** el riesgo de la oferta individual. La interfaz de
cobertura y el factor informativo de 0 puntos lo especifican
expresamente. El historial del vendedor, precio y reportes comunitarios
se evalúan por separado.

Si el backend rechaza el proceso con un `ResponseStatusException`,
el controlador de extensión devuelve un JSON ProblemDetail con el
motivo concreto en `detail` (por ejemplo, «No se pudo normalizar un
precio válido» o «Hay una inconsistencia entre los identificadores»).
El popup prioriza ese texto antes del mensaje genérico del estado HTTP.
Los errores de origen desconocido siguen requiriendo consultar los
logs y `GET /api/extension/imports/{id}/normalized`.


## User Products con nombre abreviado en JSON-LD

Algunas fichas `/up/MLAU...` presentan un título de página con condición
(ej. `Samsung A13 (Usado)`) y un JSON-LD `Product` de nombre abreviado
(`Samsung A13`). El `Product` puede no tener `url` directa, pero sí
`sku`, `productID` y `offers.url` que identifican el mismo `MLAU`.

Para evitar falsos rechazos, `isCurrentProduct` ahora confirma la identidad
por esos identificadores cuando son inequívocos; si cualquiera de los ID
explícitos apunta a otro producto, el JSON-LD se rechaza. El nombre
es solo una alternativa cuando no hay referencias estructuradas fiables.

Caso reproducido con el HTML del Samsung A13 usado, `MLAU3344351864`:
el precio visible es `111.552 ARS`, el vendedor `FERNANDOZUZULIA`,
el JSON-LD tiene `offers.priceCurrency=ARS`, pero no hay
`meta[property="product:price:currency"]`. Antes, el nombre abreviado
hacía que se descartara el JSON-LD, dejando `currency=null` y causando
el potencial rechazo `422` al procesar la importación. La nueva
prueba de regresión comprueba que se conserva `ARS` sin aceptar
calificaciones ni moneda de otro producto.

## Opiniones propias del ítem MLA (tercer factor condicional)

No toda estrella observada debajo del título pertenece a la oferta.
El JSON-LD de un modelo (`MLAU` o `/p/MLA`) puede agregar experiencias
de múltiples tiendas. Por eso se mantienen las variables `ml_product_*`
como **contexto no puntuable** y se crean variables separadas en
`V31__ml_item_verified_reviews.sql`:

- `ml_item_review_rating`, `ml_item_review_count`
- `ml_item_review_verified_id`, `ml_item_review_observed_at`

Para publicaciones `LISTING` con `external_id=MLA...` y URL que no
sea una ficha de catálogo `/p/MLA...`, el tercer factor
`ITEM_REVIEWS` queda pendiente hasta corroborarlo. Durante una nueva
evaluación autenticada, el backend consulta
`GET https://api.mercadolibre.com/reviews/item/{ITEM_ID}` y exige:

1. Que haya una muestra publicada de reseñas.
2. Que todas las reseñas muestreadas tengan
   `reviewable_object.type=item` y el mismo `reviewable_object.id`
   que el MLA analizado, sin mezcla con reseñas de producto.
3. Que `rating_average` y `paging.total` sean válidos, con un recuento
   al menos igual a la muestra.
4. Que la evidencia persistida sea reciente (máximo 30 días).
   Las observaciones concluyentes se reutilizan durante 24 horas;
   los errores de API se consideran inconclusos y pueden reintentarse.

Resultados posibles:

| Publicación | Cobertura ilustrativa | Interpretación |
| --- | --- | --- |
| `/up/MLAU...` o producto de catálogo `/p/MLA...` | `2/2` | Reseñas agregadas del modelo no cuentan |
| Ítem MLA sin evidencia propia corroborada | `2/3` | La verificación de reseñas del ítem falta o no está disponible |
| Ítem MLA con reseñas propias corroboradas | `3/3` | Hay compras anteriores asociadas al ítem, no garantía de seguridad |

**Requisito operacional:** La API oficial de reseñas requiere un
access token OAuth. Los perfiles locales `demo` y `registration`
deshabilitan OAuth: no se simula el respaldo del tercer factor.
En modo sin OAuth, la evaluación de precio, vendedor y descripción
continúa normalmente. El perfil opt-in `connected` permite probar
**una sola cuenta de integración administrada por el servidor** para
consultar reseñas públicas, sin solicitar OAuth a cada usuario final
de la extensión. La factibilidad real se verifica con ítems de distintos
vendedores; consultar [ML-CENTRAL-OAUTH-TRIAL.md](ML-CENTRAL-OAUTH-TRIAL.md).
En esta prueba los tokens siguen en memoria: no hay persistencia ni
renovación automática hasta que se compruebe la hipótesis.
Las importaciones antiguas y el JSON-LD por sí solos no pueden convertirse
retroactivamente en `ITEM_REVIEWS` verificado.

La señal `ML_ITEM_REVIEWS_VERIFIED` se muestra con **0 puntos** para
no restar gravedad a un precio extremo ni transformar satisfacción
con el producto en reputación verificada del vendedor. La evidencia
contribuye a la **cobertura**, no certifica que una venta futura sea
segura. Las evaluaciones ya persistidas se conservan.
