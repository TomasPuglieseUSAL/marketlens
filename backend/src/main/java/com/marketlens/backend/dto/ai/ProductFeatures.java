package com.marketlens.backend.dto.ai;

import java.util.List;

public record ProductFeatures(
        String productType,
        String brand,
        String model,
        String variant,
        Integer storageGb,
        String condition,
        List<String> accessoriesMentioned,
        String packaging
) {
}