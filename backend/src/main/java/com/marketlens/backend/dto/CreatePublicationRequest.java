package com.marketlens.backend.dto;

import com.marketlens.backend.entity.Marketplace;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreatePublicationRequest(

    @NotBlank(message = "Title is required")
    String title,

    String description,

    @NotNull(message = "Price is required")
    @PositiveOrZero(message = "Price cannot be negative")
    BigDecimal price,

    @NotNull(message = "Seller is required")
    Long sellerId,

    @NotNull(message = "Marketplace is required")
    Marketplace marketplace,

    String externalId,

    String originalUrl

) {
}