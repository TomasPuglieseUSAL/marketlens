package com.marketlens.backend.dto;

import java.time.LocalDateTime;

public record SellerSalesObservationResponse(
        String status,
        Integer completedSales,
        LocalDateTime salesObservedAt
) {}
