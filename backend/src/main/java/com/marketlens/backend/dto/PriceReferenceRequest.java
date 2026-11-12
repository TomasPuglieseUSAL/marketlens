package com.marketlens.backend.dto;

import java.util.List;

public record PriceReferenceRequest(
        List<Long> referenceIds
) {
}