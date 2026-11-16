-- La página de un producto Temu puede no identificar a un vendedor individual.
-- NULL significa identidad desconocida, no una identidad ficticia.
ALTER TABLE publications ALTER COLUMN seller_id DROP NOT NULL;
