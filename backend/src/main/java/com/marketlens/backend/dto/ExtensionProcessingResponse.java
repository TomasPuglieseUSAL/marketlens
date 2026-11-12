package com.marketlens.backend.dto;

public record ExtensionProcessingResponse(
    Long importId,
    Long publicationId,
    String status,
    boolean created
) {
}