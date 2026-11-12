package com.marketlens.backend.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SellerCommercialTextParserTest {
    @Test void extraeCantidadExacta() {
        assertEquals(3, SellerCommercialTextParser.parseExactListedProducts("3 productos"));
        assertEquals(1234, SellerCommercialTextParser.parseExactListedProducts("1.234 productos"));
    }

    @Test void rechazaCantidadesAproximadasOAmbiguas() {
        assertNull(SellerCommercialTextParser.parseExactListedProducts("más de 3 productos"));
        assertNull(SellerCommercialTextParser.parseExactListedProducts("+3 productos"));
        assertNull(SellerCommercialTextParser.parseExactListedProducts("3 ventas"));
        assertNull(SellerCommercialTextParser.parseExactListedProducts(null));
        assertNull(SellerCommercialTextParser.parseExactListedProducts("999999999999999 productos"));
    }

    @Test void requiereDeclaracionExplicitaDeInsuficiencia() {
        assertTrue(SellerCommercialTextParser.explicitlyInsufficientSales(
                "No tiene suficientes ventas para evaluar su atención"));
        assertTrue(SellerCommercialTextParser.explicitlyInsufficientSales(
                "No cuenta con suficientes ventas"));
        assertFalse(SellerCommercialTextParser.explicitlyInsufficientSales("3 productos"));
        assertFalse(SellerCommercialTextParser.explicitlyInsufficientSales(null));
        assertFalse(SellerCommercialTextParser.explicitlyInsufficientSales(
                "El vendedor no tiene suficientes ventas para evaluar su atención"));
    }
}
