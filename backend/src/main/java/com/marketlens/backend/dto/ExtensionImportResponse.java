package com.marketlens.backend.dto;

import java.time.Instant;

public record ExtensionImportResponse(
    Long id,
    String status,
    String marketplace,
    String title,
    String seller,
    Instant createdAt
) {
}