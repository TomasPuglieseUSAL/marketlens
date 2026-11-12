package com.marketlens.backend.dto;

public record MercadoLibreItemResponse(
    String id,
    String title,
    Long sellerId,
    String userProductId,
    String permalink
) {
}