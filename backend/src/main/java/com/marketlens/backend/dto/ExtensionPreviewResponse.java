package com.marketlens.backend.dto;

import java.util.List;

public record ExtensionPreviewResponse(
    boolean accepted,
    ExtensionPreviewRequest publication,
    List<String> missingFields
) {
}