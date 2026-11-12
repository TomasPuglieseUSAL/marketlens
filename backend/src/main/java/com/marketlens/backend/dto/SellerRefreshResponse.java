package com.marketlens.backend.dto;

import java.time.LocalDateTime;

public record SellerRefreshResponse(
        String status,
        Integer completedSales,
        LocalDateTime salesObservedAt
) {
}
