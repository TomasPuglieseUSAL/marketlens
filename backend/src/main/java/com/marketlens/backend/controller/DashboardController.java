package com.marketlens.backend.controller;

import com.marketlens.backend.dto.DashboardSummaryResponse;
import com.marketlens.backend.dto.MarketplaceStatisticsResponse;
import com.marketlens.backend.service.DashboardService;
import com.marketlens.backend.service.MarketplaceStatisticsService;
import com.marketlens.backend.service.MarketplaceStatisticsService;
import java.util.List;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final MarketplaceStatisticsService marketplaceStatisticsService;

    public DashboardController(
        DashboardService dashboardService,
        MarketplaceStatisticsService marketplaceStatisticsService) {
            this.dashboardService = dashboardService;
            this.marketplaceStatisticsService = marketplaceStatisticsService;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse getSummary() {
        return dashboardService.getSummary();
    }

    @GetMapping("/marketplaces")
    public List<MarketplaceStatisticsResponse> getMarketplaceStatistics() {
        return marketplaceStatisticsService.getStatistics();
    }
}