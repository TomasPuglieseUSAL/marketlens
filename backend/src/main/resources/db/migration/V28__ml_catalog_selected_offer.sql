-- La ficha de catálogo /p/MLA identifica un producto, no una oferta.
-- Guardar la referencia MLA seleccionada explícitamente en el formulario
-- de compra sin fabricar una URL de publicación individual.
ALTER TABLE extension_imports
    ADD COLUMN selected_offer_id VARCHAR(120);
