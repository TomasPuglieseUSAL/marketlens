package com.marketlens.backend.service;

/** Ponderación experimental y acotada de denuncias de usuarios independientes. */
public final class CommunityReportRiskScoring {
    public record Signal(String code, String description, int points) { }

    private CommunityReportRiskScoring() { }

    public static int points(int uniqueReporters) {
        if (uniqueReporters >= 4) return 8;
        if (uniqueReporters >= 2) return 3;
        return 0;
    }

    public static Signal evaluate(int uniqueReporters) {
        if (uniqueReporters <= 0) return null;
        int points = points(uniqueReporters);
        String noun = uniqueReporters == 1 ? "usuario" : "usuarios";
        String detail = uniqueReporters == 1
                ? "Reporte comunitario reciente de 1 usuario (no verificado)."
                : "Reportes comunitarios recientes de " + uniqueReporters
                    + " " + noun + " distintos (no verificados).";
        return new Signal("COMMUNITY_REPORTS", detail, points);
    }
}
