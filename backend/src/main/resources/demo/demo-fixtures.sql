-- MarketLens: datos 100% ficticios, solo para modo demo.
-- No forma parte del historial Flyway; se ejecuta únicamente con perfil demo.
-- Las identidades e IDs DEMO_* no son publicaciones ni vendedores reales.
-- Cada INSERT es idempotente gracias a las claves únicas actuales.

INSERT INTO sellers
  (name, marketplace, external_seller_id, completed_sales, sales_observed_at, account_age_days)
VALUES
  ('[DEMO] Vendedor Inicial', 'MERCADO_LIBRE', 9000000101, 1, CURRENT_TIMESTAMP, 140)
ON CONFLICT DO NOTHING;

INSERT INTO sellers
  (name, marketplace, external_seller_id, completed_sales, sales_observed_at, account_age_days)
VALUES
  ('[DEMO] Vendedor Establecido', 'MERCADO_LIBRE', 9000000102, 120, CURRENT_TIMESTAMP, 940)
ON CONFLICT DO NOTHING;

INSERT INTO sellers
  (name, marketplace, external_seller_id, completed_sales, sales_observed_at, account_age_days)
VALUES
  ('[DEMO] Vendedor Sin Historial', 'MERCADO_LIBRE', 9000000103, NULL, NULL, NULL)
ON CONFLICT DO NOTHING;

INSERT INTO publications
  (title, description, price, seller_id, marketplace, external_id, original_url, reference_type)
SELECT
  '[DEMO] Consola Solaris Slim 1 TB usada - Oferta muy económica',
  'Publicación sintética de demostración; no corresponde a ninguna oferta real.',
  690000.00, s.id, 'MERCADO_LIBRE', 'DEMO_SOLARIS_01', NULL, 'LISTING'
FROM sellers s
WHERE s.marketplace = 'MERCADO_LIBRE' AND s.external_seller_id = 9000000101
ON CONFLICT DO NOTHING;

INSERT INTO publications
  (title, description, price, seller_id, marketplace, external_id, original_url, reference_type)
SELECT
  '[DEMO] Consola Solaris Slim 1 TB usada',
  'Publicación sintética de demostración; no corresponde a ninguna oferta real.',
  1380000.00, s.id, 'MERCADO_LIBRE', 'DEMO_SOLARIS_02', NULL, 'LISTING'
FROM sellers s
WHERE s.marketplace = 'MERCADO_LIBRE' AND s.external_seller_id = 9000000102
ON CONFLICT DO NOTHING;

INSERT INTO publications
  (title, description, price, seller_id, marketplace, external_id, original_url, reference_type)
SELECT
  '[DEMO] Consola Solaris Slim 1 TB usada excelente estado',
  'Publicación sintética de demostración; no corresponde a ninguna oferta real.',
  1420000.00, s.id, 'MERCADO_LIBRE', 'DEMO_SOLARIS_03', NULL, 'LISTING'
FROM sellers s
WHERE s.marketplace = 'MERCADO_LIBRE' AND s.external_seller_id = 9000000102
ON CONFLICT DO NOTHING;

INSERT INTO publications
  (title, description, price, seller_id, marketplace, external_id, original_url, reference_type)
SELECT
  '[DEMO] Consola Solaris Slim 1 TB usada con caja',
  'Publicación sintética de demostración; no corresponde a ninguna oferta real.',
  1450000.00, s.id, 'MERCADO_LIBRE', 'DEMO_SOLARIS_04', NULL, 'LISTING'
FROM sellers s
WHERE s.marketplace = 'MERCADO_LIBRE' AND s.external_seller_id = 9000000102
ON CONFLICT DO NOTHING;

INSERT INTO publications
  (title, description, price, seller_id, marketplace, external_id, original_url, reference_type)
SELECT
  '[DEMO] Consola Solaris Slim 1 TB usada',
  'Publicación sintética de demostración; no corresponde a ninguna oferta real.',
  1390000.00, s.id, 'MERCADO_LIBRE', 'DEMO_SOLARIS_05', NULL, 'LISTING'
FROM sellers s
WHERE s.marketplace = 'MERCADO_LIBRE' AND s.external_seller_id = 9000000103
ON CONFLICT DO NOTHING;

INSERT INTO publications
  (title, description, price, seller_id, marketplace, external_id, original_url, reference_type)
SELECT
  '[DEMO] Consola Solaris Slim 1 TB usada buen estado',
  'Publicación sintética de demostración; no corresponde a ninguna oferta real.',
  1500000.00, s.id, 'MERCADO_LIBRE', 'DEMO_SOLARIS_06', NULL, 'LISTING'
FROM sellers s
WHERE s.marketplace = 'MERCADO_LIBRE' AND s.external_seller_id = 9000000102
ON CONFLICT DO NOTHING;

INSERT INTO publications
  (title, description, price, seller_id, marketplace, external_id, original_url, reference_type)
SELECT
  '[DEMO] Consola Solaris Slim 1 TB usada con accesorios básicos',
  'Publicación sintética de demostración; no corresponde a ninguna oferta real.',
  1470000.00, s.id, 'MERCADO_LIBRE', 'DEMO_SOLARIS_07', NULL, 'LISTING'
FROM sellers s
WHERE s.marketplace = 'MERCADO_LIBRE' AND s.external_seller_id = 9000000103
ON CONFLICT DO NOTHING;

INSERT INTO publications
  (title, description, price, seller_id, marketplace, external_id, original_url, reference_type)
SELECT
  '[DEMO] Consola Solaris Slim 1 TB usada',
  'Publicación sintética de demostración; no corresponde a ninguna oferta real.',
  1400000.00, s.id, 'MERCADO_LIBRE', 'DEMO_SOLARIS_08', NULL, 'LISTING'
FROM sellers s
WHERE s.marketplace = 'MERCADO_LIBRE' AND s.external_seller_id = 9000000102
ON CONFLICT DO NOTHING;

