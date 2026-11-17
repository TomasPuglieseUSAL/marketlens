package com.marketlens.backend.service;

import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Reseñas del ITEM de Mercado Libre, no del modelo / producto de catálogo.
 * La procedencia es corroborada por la API: type=item e id=MLA concreto.
 * No aceptar métricas del DOM ni JSON-LD como prueba de esta asociación.
 */
public final class MercadoLibreItemReviewEvidence {
    public static final int MAX_AGE_DAYS = 30;
    public record Verified(String itemId, BigDecimal rating, int count) { }

    private MercadoLibreItemReviewEvidence() { }

    public static boolean eligible(Publication publication) {
        return publication != null && eligible(
                publication.getMarketplace() == null ? null : publication.getMarketplace().name(),
                publication.getReferenceType(),
                publication.getExternalId(),
                publication.getOriginalUrl());
    }

    public static boolean eligible(String marketplace, String referenceType,
                                   String externalId, String originalUrl) {
        return "MERCADO_LIBRE".equals(marketplace)
                && "LISTING".equals(referenceType)
                && externalId != null
                && externalId.matches("MLA[1-9]\\d{5,}")
                && (originalUrl == null
                    || !originalUrl.matches(
                        "(?i)^https?://[^/]+/(?:[^/]+/)?p/MLA\\d{6,}/?(?:[?#].*)?$"));
    }

    public static boolean hasVerifiedEvidence(Publication publication, LocalDateTime now) {
        return eligible(publication)
                && publication.getMlItemReviewVerifiedId() != null
                && publication.getExternalId().equals(publication.getMlItemReviewVerifiedId())
                && MercadoLibreProductReviewEvidence.hasMetrics(
                    publication.getMlItemReviewRating(), publication.getMlItemReviewCount())
                && publication.getMlItemReviewObservedAt() != null
                && !publication.getMlItemReviewObservedAt().isAfter(now)
                && !publication.getMlItemReviewObservedAt().isBefore(now.minusDays(MAX_AGE_DAYS));
    }

    /**
     * La respuesta a /reviews/item/MLA... puede contener reseñas de catálogo.
     * Se exige que TODAS las muestras devueltas acrediten el MISMO item.
     * Rechazar faltantes, mezclas, muestras vacías y métricas contradictorias.
     */
    public static Verified fromApiResponse(String itemId, JsonNode root) {
        if (itemId == null || !itemId.matches("MLA[1-9]\\d{5,}")
                || root == null || !root.isObject()
                || root.path("cross_site_enabled").asBoolean(false)) return null;

        JsonNode sample = root.path("reviews");
        JsonNode total = root.path("paging").path("total");
        JsonNode average = root.path("rating_average");
        if (!sample.isArray() || sample.size() == 0
                || !total.isIntegralNumber() || !total.canConvertToInt()
                || total.intValue() < sample.size() || total.intValue() > 10000000
                || !average.isNumber()) return null;

        BigDecimal rating = average.decimalValue();
        if (rating.compareTo(BigDecimal.ZERO) <= 0
                || rating.compareTo(new BigDecimal("5.0")) > 0) return null;

        for (JsonNode review : sample) {
            JsonNode reviewedObject = review.path("reviewable_object");
            if (!"item".equalsIgnoreCase(reviewedObject.path("type").asString(""))
                    || !itemId.equalsIgnoreCase(reviewedObject.path("id").asString(""))
                    || !"published".equalsIgnoreCase(review.path("status").asString(""))) {
                return null;
            }
        }

        return new Verified(itemId, rating.setScale(1, RoundingMode.HALF_UP),
                total.intValue());
    }

    public static String description(Publication publication) {
        String average = publication.getMlItemReviewRating()
                .setScale(1, RoundingMode.HALF_UP)
                .toPlainString().replace('.', ',');
        return "Opiniones de compradores vinculadas a esta publicación ("
                + publication.getMlItemReviewCount() + "): "
                + average + "/5. Verificadas por identificador de ítem en Mercado Libre; "
                + "no garantizan por sí solas la confiabilidad de la transacción.";
    }
}
