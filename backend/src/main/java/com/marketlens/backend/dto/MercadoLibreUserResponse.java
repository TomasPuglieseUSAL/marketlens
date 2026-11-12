package com.marketlens.backend.dto;

public record MercadoLibreUserResponse(
        Long id,
        String nickname,
        String registrationDate,
        String reputationLevel,
        String powerSellerStatus,
        Integer completedSales,
        String salesPeriod
) {
}
