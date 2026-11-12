package com.marketlens.backend.dto;

public record ExtensionReferenceResponse(
    Long importId,
    Long referenceId,
    String referenceType,
    String externalId,
    String status
) {
}