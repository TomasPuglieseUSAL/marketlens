# Integración experimental de Temu (MVP)

Reconoce `goods.html?goods_id=<id>` y enlaces de producto `*-g-<id>.html`.
La extensión lee solamente la página abierta: JSON-LD Product y metadatos del producto; no usa endpoints privados.

- **Moneda:** solo ARS explícita. No hay conversiones automáticas de divisas ni inferencias a partir del símbolo `$`.
- **Vendedor:** si no existe evidencia de una cuenta individual, queda como `seller_id = NULL` y se visualiza "No identificado".
- **Cobertura:** no se inventan reputación ni antigüedad; la clasificación puede ser `INDETERMINATE`.
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
