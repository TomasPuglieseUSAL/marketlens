package com.marketlens.backend.service;

import com.marketlens.backend.dto.RiskCoverageFactorResponse;
import com.marketlens.backend.dto.RiskCoverageResponse;
import com.marketlens.backend.entity.ExtensionImport;
import com.marketlens.backend.entity.MarketplaceReference;
import com.marketlens.backend.repository.ExtensionImportRepository;
import com.marketlens.backend.dto.SellerPublicSignalsResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ExtensionRiskCoverageService {

        private final ExtensionImportRepository importRepository;

        public ExtensionRiskCoverageService(
                        ExtensionImportRepository importRepository) {

                this.importRepository = importRepository;
        }

        @Transactional(readOnly = true)
        public RiskCoverageResponse getCoverage(
                        Long importId,
                        String username) {

                // Solo consultar importaciones del usuario autenticado.
                ExtensionImport imported = importRepository
                                .findByIdAndImportedBy(importId, username)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Importación no encontrada."));

                // -------------------------------------
                // Disponibilidad de factores de riesgo
                // -------------------------------------

                // 1. Descripción de la publicación.
                boolean hasDescription = imported.getDescription() != null &&
                                !imported.getDescription().isBlank();

                // 2. Reputación del vendedor.
                // El nivel de Mercado Libre debe estar entre 1 y 5.
                Integer reputationLevel = imported.getSellerReputationLevel();

                boolean hasReputation = reputationLevel != null &&
                                reputationLevel >= 1 &&
                                reputationLevel <= 5;

                // 3. Antigüedad de la cuenta.
                // Para Facebook utilizamos el año público de incorporación.
                // Este dato indica disponibilidad de información,
                // no una antigüedad exacta en días.
                Integer facebookJoinYear = imported.getFacebookJoinYear();

                boolean hasAccountAge = facebookJoinYear != null;

                List<RiskCoverageFactorResponse> factors = List.of(
                                new RiskCoverageFactorResponse(
                                                "DESCRIPTION",
                                                "Descripción de la publicación",
                                                hasDescription),
                                new RiskCoverageFactorResponse(
                                                "SELLER_REPUTATION",
                                                "Reputación del vendedor",
                                                hasReputation),
                                new RiskCoverageFactorResponse(
                                                "SELLER_ACCOUNT_AGE",
                                                "Antigüedad de la cuenta",
                                                hasAccountAge));

                int availableFactors = (int) factors.stream()
                                .filter(RiskCoverageFactorResponse::available)
                                .count();

                int totalFactors = factors.size();

                String status;

                if (availableFactors == totalFactors) {
                        status = "COMPLETE";

                } else if (availableFactors == 0) {
                        status = "INSUFFICIENT";

                } else {
                        status = "PARTIAL";
                }

                MarketplaceReference reference = imported.getMarketplaceReference();

                String referenceType = reference != null
                                ? reference.getReferenceType()
                                : "UNKNOWN";

                SellerPublicSignalsResponse sellerSignals = new SellerPublicSignalsResponse(
                                imported.getSellerName(),
                                imported.getSellerProfileUrl(),
                                imported.getSellerBadge(),
                                imported.getSellerSalesText(),
                                imported.getSellerReputationLevel());

                return new RiskCoverageResponse(
                                imported.getId(),
                                referenceType,
                                status,
                                availableFactors,
                                totalFactors,
                                factors,
                                sellerSignals);
        }
}