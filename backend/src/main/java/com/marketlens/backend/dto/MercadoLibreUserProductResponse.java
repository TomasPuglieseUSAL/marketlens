package com.marketlens.backend.dto;

public record MercadoLibreUserProductResponse(
    String id,
    String name,
    Long sellerId,
    String domainId,
    String familyId
) {
}