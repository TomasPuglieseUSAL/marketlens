package com.marketlens.backend.dto;

import com.marketlens.backend.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(

    @NotNull(message = "Report reason is required")
    ReportReason reason,

    @Size(
        max = 1000,
        message = "Report description cannot exceed 1000 characters"
    )
    String description

) {
}