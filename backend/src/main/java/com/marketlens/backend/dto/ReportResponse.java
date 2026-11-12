package com.marketlens.backend.dto;

import com.marketlens.backend.entity.ReportReason;

import java.time.LocalDateTime;

public record ReportResponse(
    Long id,
    Long publicationId,
    Long userId,
    ReportReason reason,
    String description,
    LocalDateTime createdAt
) {
}