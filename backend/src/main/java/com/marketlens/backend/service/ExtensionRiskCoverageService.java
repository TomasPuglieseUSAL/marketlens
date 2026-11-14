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
                // La cobertura refleja DISPONIBILIDAD del dato, no el grado
                // de confianza. El ID de tienda y la cantidad de valoraciones
                // se utilizan por separado para medir su respaldo.
                if ("TEMU".equals(imported.getMarketplace())
                        && imported.getSellerName() != null
                        && !imported.getSellerName().isBlank()
                        && Boolean.TRUE.equals(imported.getTemuSellerSectionObserved())
                        && TemuSellerRiskScoring.isValidSellerRating(imported.getTemuSellerRating())) {
                    hasReputation = true;
                }

                // La antigüedad deja de ser una dimensión obligatoria.
                // Sólo Temu dispone por ahora de extracción de opiniones del producto.
                List<RiskCoverageFactorResponse> factors = new java.util.ArrayList<>();
                factors.add(new RiskCoverageFactorResponse(
                        "DESCRIPTION", "Información del producto", hasDescription));
                factors.add(new RiskCoverageFactorResponse(
                        "SELLER_REPUTATION", "Reputación del vendedor", hasReputation));
                if ("TEMU".equals(imported.getMarketplace())) {
                        factors.add(new RiskCoverageFactorResponse(
                                "PRODUCT_REVIEWS", "Opiniones del producto",
                                TemuProductReviewEvidence.hasMetrics(
                                        imported.getTemuProductRating(),
                                        imported.getTemuProductReviewCount())));
                }

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