-- Distingue falta observada de puntuación pública de extracción desconocida.
ALTER TABLE extension_imports ADD COLUMN ml_product_rating_unavailable BOOLEAN;
ALTER TABLE publications ADD COLUMN ml_product_rating_unavailable BOOLEAN;
