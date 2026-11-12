package com.marketlens.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record ExtensionNormalizationResponse(
    Long importId,
    String marketplace,
    String title,
    BigDecimal price,
    String currency,
    String referenceType,
    String referenceId,
    String sellerName,
    List<String> warnings
) {
}