package com.marketlens.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateSellerRequest(

    String name,

    @DecimalMin(
        value = "0.0",
        message = "Seller reputation must be between 0 and 1"
    )
    @DecimalMax(
        value = "1.0",
        message = "Seller reputation must be between 0 and 1"
    )
    BigDecimal reputation,

    @PositiveOrZero(
        message = "Seller account age cannot be negative"
    )
    Integer accountAgeDays

) {
}