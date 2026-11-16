package com.marketlens.backend.dto;

import com.marketlens.backend.entity.ReportReason;

public record CommunityReportReasonResponse(ReportReason reason, int count) { }
