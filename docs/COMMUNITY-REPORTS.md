# Reportes comunitarios en MarketLens (MVP)

## Flujo

1. Al analizar una publicación, el backend identifica la publicación interna.
2. `RiskEvaluationService` consulta la señal comunitaria **actual** y guarda en la
   evaluación un factor `COMMUNITY_REPORTS` cuando hay reportes recientes.
3. El popup consulta `GET /api/publications/{publicationId}/reports/summary` y
   muestra un panel compacto **solo si hay reportes recientes**. El frontend
   también muestra este resumen automáticamente encima del formulario cerrado.
4. Tras enviar una denuncia, se refresca el resumen. El puntaje de una
   evaluación anterior **no se recalcula automáticamente**: hay que analizar
   nuevamente para incorporar la evidencia nueva.

## Criterio experimental

- Ventana de análisis: últimos **90 días**.
- Solo cuenta el **último reporte válido de cada usuario por publicación** para
  definir la contribución y los motivos. Reportes más antiguos o futuros se
  excluyen del resumen.
- 0 usuarios: no se agrega factor.
- 1 usuario: factor informativo **+0**.
- 2–3 usuarios: **+3**.
- 4 o más usuarios: **+8** como máximo.
- El recuento de denuncias recientes puede superar al de usuarios distintos,
  pero solo estos últimos determinan los puntos.
- El factor es independiente de la cobertura (por ejemplo, 3/3) y aparece como
  parte del puntaje histórico de la evaluación recién creada.
- Los reportes son **alegaciones no verificadas**. Ni el número de denuncias
  ni sus motivos demuestran por sí solos que una publicación sea fraudulenta.

## Presentación y privacidad

El resumen muestra cantidad total reciente, cantidad de usuarios distintos,
hasta tres motivos principales y la contribución máxima que tendría una nueva
evaluación. No incluye autores, correos ni descripciones de texto libre.

Esta implementación reduce el peso de denuncias repetidas por una misma
cuenta, pero **no resuelve** campañas coordinadas con múltiples cuentas.
Antes de aumentar los pesos deben incorporarse mecanismos de moderación,
límites de reporte, estados de verificación y auditoría de abuso.

El histórico de reportes completo sigue accesible en la sección manual del
frontend. El resumen no representa todos los reportes de todos los tiempos.
