package com.marketlens.backend.service;

import com.marketlens.backend.dto.RiskCoverageFactorResponse;
import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.RiskCoverageStatus;
import com.marketlens.backend.entity.RiskEvaluation;
import com.marketlens.backend.entity.Seller;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class RiskCoverageCalculator {

    public static boolean hasSellerEvidence(Publication publication, LocalDateTime now) {
        Seller seller = publication.getSeller();
        if (seller == null) return false;
        Integer level = seller.getReputationLevel();
        BigDecimal legacy = seller.getReputation();
        return (level != null && level >= 1 && level <= 5)
                || (legacy != null && legacy.compareTo(BigDecimal.ZERO) >= 0
                    && legacy.compareTo(BigDecimal.ONE) <= 0)
                || SellerActivityEvidence.detect(seller, now)
                    != SellerActivityEvidence.ActivitySignal.NONE
                || TemuSellerRiskScoring.hasObservedReputation(publication, now)
                || MercadoLibreSellerEvidence.hasObservedReputation(publication, now);
    }

    /**
     * Un solo criterio para el conteo y el detalle que recibe la extensión.
     * Las reseñas del PRODUCTO no se confunden con señales del vendedor.
     */
    public static List<RiskCoverageFactorResponse> factors(Publication publication) {
        LocalDateTime now = LocalDateTime.now();
        List<RiskCoverageFactorResponse> result = new ArrayList<>();
        String description = publication.getDescription();
        result.add(new RiskCoverageFactorResponse("DESCRIPTION",
                "Información del producto", description != null && !description.isBlank()));
        result.add(new RiskCoverageFactorResponse("SELLER_REPUTATION",
                "Reputación o actividad del vendedor", hasSellerEvidence(publication, now)));

        Marketplace marketplace = publication.getMarketplace();
        if (marketplace == Marketplace.TEMU || marketplace == Marketplace.MERCADO_LIBRE) {
            boolean hasReviews = marketplace == Marketplace.TEMU
                    ? TemuProductReviewEvidence.hasObservedEvidence(publication, now)
                    : MercadoLibreProductReviewEvidence.hasObservedEvidence(publication, now);
            result.add(new RiskCoverageFactorResponse("PRODUCT_REVIEWS",
                    marketplace == Marketplace.MERCADO_LIBRE
                        ? "Calificación general del producto (no del vendedor)"
                        : "Calificación y opiniones del producto", hasReviews));
        }
        return List.copyOf(result);
    }

    public void applyCoverage(Publication publication, RiskEvaluation evaluation) {
        List<RiskCoverageFactorResponse> evidence = factors(publication);
        int available = (int) evidence.stream().filter(RiskCoverageFactorResponse::available).count();
        int total = evidence.size();
        RiskCoverageStatus status = available == total ? RiskCoverageStatus.COMPLETE
                : available == 0 ? RiskCoverageStatus.INSUFFICIENT
                : RiskCoverageStatus.PARTIAL;

        evaluation.setCoverageStatus(status);
        evaluation.setAvailableFactors(available);
        evaluation.setTotalFactors(total);
    }
}
