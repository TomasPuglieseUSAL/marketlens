package com.marketlens.backend.dto;

/**
 * Observaciones visuales extraídas del panel del vendedor en una publicación.
 * Los textos visibles no constituyen una certificación de ventas históricas por API.
 */
public record SellerSalesObservationRequest(
        String marketplace,
        String externalId,
        String sellerName,
        String sellerSalesText,
        String sellerListedProductsText,
        String sellerInsufficientSalesText
) {}
