# Integración experimental de Temu (MVP)

Reconoce `goods.html?goods_id=<id>` y enlaces de producto `*-g-<id>.html`.
La extensión lee solamente la página abierta: JSON-LD Product y metadatos del producto; no usa endpoints privados.

- **Moneda:** solo ARS explícita. No hay conversiones automáticas de divisas ni inferencias a partir del símbolo `$`.
- **Vendedor:** cuando la página identifica una tienda, como `Tokki Shop`, la extensión intenta recuperar el nombre desde el panel cercano a `Star Seller`, un enlace de tienda o JSON-LD. Se crea un registro de vendedor observado para esa publicación, sin tratarlo como identidad comercial verificada ni unirlo automáticamente a otras ofertas. Si no se logra identificar, `seller_id = NULL` y se visualiza "No identificado".
- **Star Seller (opcional):** la identificación del vendedor es independiente de ese distintivo; también se reconocen tiendas normales cuando el sitio muestra «Sold by», «Seller», «Store» o una tienda enlazada. Si se observa Star Seller, se almacena en la importación y publicación (`seller_badge`, migración V21) y se muestra como factor informativo de **0 puntos**. La ausencia de la etiqueta no implica mala reputación ni incrementa puntos de riesgo: puede no estar visible o simplemente no corresponder al vendedor. El extractor no infiere un estado de ausencia verificada.
- **Corrección:** el campo de vendedor en Temu es editable. Los importes ARS posibles aparecen como opciones destacadas y seleccionables; elegir uno llena el campo de precio. La persona debe verificar la variante y las promociones aplicables.
- **Calificación de tienda (V22):** el extractor distingue el texto de calificación del PRODUCTO (`4.7 out of five stars`) de un valor cercano a la identidad de la TIENDA (`Tokki Shop`, `4.8/5`). Se guarda `temu_seller_rating` en la importación y en la publicación, se muestra en el frontend y como señal de análisis de **0 puntos**. Solo se acepta si puede asociarse a un nombre de tienda identificado; no se copia `aggregateRating` del producto. El dato todavía no se verifica de manera independiente y no se utiliza para penalizar, bonificar ni confirmar cobertura de reputación.
- **Corrección del falso vendedor:** se rechazan nombres como `4.7 out of five stars`. Un registro previo con exactamente ese formato podrá actualizar su asociación a la tienda correcta sin modificar vendedores con nombres válidos.
- **Cobertura:** no se inventan reputación verificada ni antigüedad; la clasificación puede ser `INDETERMINATE`.
- **Precios:** no hay comparación automática para Temu ni se usan sus productos como referencias de otras plataformas.
- **Identidad:** `goods_id` es un producto, que puede agrupar variantes. Si su precio cambió, la importación devuelve un conflicto en lugar de reemplazar silenciosamente el precio anterior.
- **Limitaciones:** las páginas con moneda distinta de ARS, precios ambiguos o ausencia de metadatos no podrán completarse automáticamente.

## Pruebas

1. Iniciá Docker, recargá la extensión y abrí un producto de Temu.
2. Si ofrece metadatos de precio en ARS, analizá y comprobá que el vendedor pueda figurar como desconocido.
3. Presioná "Ver en MarketLens" y comprobá que el detalle corresponda al producto.
4. Comprobá que el resultado no se presente como riesgo bajo con cobertura insuficiente.
5. Repetí los análisis de Mercado Libre y Facebook Marketplace para verificar regresiones.
6. Comprobá el backend con `cd backend && ./mvnw test` y el frontend con `cd frontend && npm ci && npm run build`.

Necesitamos probar productos reales de Temu en distintos navegadores: el DOM y los metadatos pueden variar por país, dispositivo y sesión.


## Identidad, volumen de reseñas y reglas de riesgo (V23)

La extensión busca `mall_id` en enlaces de tienda (`mall.html?mall_id=...` y enlaces `-m-<id>.html`).
El `goods_id` del producto NO identifica a la tienda.
Se registra `temu_seller_review_count` **solo** cuando el panel de tienda muestra un
recuento exacto explícito de reseñas de tienda; ni los comentarios del producto,
ni las ventas, ni las cifras abreviadas (por ejemplo, `1.2K+`) se convierten en
recuentos exactos. Las métricas pueden faltar si Temu cambia su interfaz.

La migración Flyway **V23** agrega estos datos a la importación y publicación,
junto con `temu_seller_section_observed` y `temu_seller_observed_at`. Esta última
fecha se registra en el backend al procesar una observación; las evidencias
solo participan del puntaje durante **30 días**.

La regla experimental `TemuSellerRiskScoring` aplica una sola señal:

| Evidencia observada | Puntos |
| --- | ---: |
| No se pudo inspeccionar sección de tienda | 0 |
| Se inspeccionó tienda pero no se muestra puntuación | **+5** |
| La tienda declara exactamente 0 reseñas | **+10** (no equivale a 0 ventas) |
| 1–4 reseñas de tienda | **+10** |
| 5–9 reseñas de tienda | **+5** |
| Al menos 10 reseñas, rating por debajo de 3,0/5 | **+15** |
| Al menos 10 reseñas, rating desde 3,0 hasta menos de 4,0/5 | **+5** |
| Al menos 10 reseñas, rating desde 4,0/5 | 0 |
| Rating sin recuento de reseñas | 0 por rating (no atribuir representatividad) |

Nunca se suman simultáneamente puntos por escasez de reseñas y calificación
baja de la misma fuente. `Star Seller` continúa siendo informativo (0 puntos).
Una reputación ausente **no** implica pocas ventas ni evidencia de fraude.

La cobertura de reputación se considera suficientemente respaldada solo
cuando existe una calificación válida de tienda, un `mall_id`, al menos
10 reseñas exactas, inspección de la sección de tienda y evidencia reciente.
El umbral de 10 y los pesos son exploratorios: necesitan pruebas con una
muestra variada de tiendas antes de usarse para decisiones importantes.

Pruebas:
```powershell
cd backend
.\mvnw.cmd test
cd ..
node --test extension/tests/temu-extractor.test.cjs
cd frontend
npm run build
cd ..
docker compose up --build -d
```
