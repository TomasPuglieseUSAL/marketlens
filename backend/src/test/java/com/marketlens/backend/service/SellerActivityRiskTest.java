package com.marketlens.backend.service;

import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.RiskEvaluation;
import com.marketlens.backend.entity.RiskLevel;
import com.marketlens.backend.entity.Seller;
import com.marketlens.backend.risk.RiskEngine;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SellerActivityRiskTest {
    private final RiskEngine engine = new RiskEngine(new RiskCoverageCalculator());

    private Publication publication(Seller seller) {
        Publication p = new Publication();
        p.setSeller(seller);
        p.setDescription("Producto usado en buen estado.");
        return p;
    }

    private void assertScoreAndCoverage(Seller seller, int score, int coverage) {
        RiskEvaluation e = engine.evaluate(publication(seller));
        assertEquals(score, (int) e.getScore());
        assertEquals(coverage, (int) e.getAvailableFactors());
        assertEquals(3, (int) e.getTotalFactors());
    }

    private void assertOneCode(Seller seller, int score, String code) {
        RiskEvaluation e = engine.evaluate(publication(seller));
        assertEquals(score, (int) e.getScore());
        assertEquals(1, e.getFactors().size());
        assertEquals(code, e.getFactors().get(0).getCode());
    }

    @Test
    void catalogoTresSinVentasVerificablesDa15YDosTercios() {
        Seller s = new Seller();
        s.setListedProductsCount(3);
        s.setProductsObservedAt(LocalDateTime.now().minusDays(1));
        assertScoreAndCoverage(s, 15, 2);
        assertOneCode(s, 15, "UNVERIFIED_SELLER_HISTORY");
        assertEquals(RiskLevel.LOW, engine.evaluate(publication(s)).getLevel());
    }

    @Test
    void ausenciaDeCifraTrasObservacionValidaDa15() {
        Seller s = new Seller();
        s.setSalesCountNotDisplayedObservedAt(LocalDateTime.now().minusDays(1));
        assertScoreAndCoverage(s, 15, 2);
        assertOneCode(s, 15, "UNVERIFIED_SELLER_HISTORY");
    }

    @Test
    void mensajeExplicitoDeVentasInsuficientesDa15() {
        Seller s = new Seller();
        s.setInsufficientSalesForReputation(true);
        s.setInsufficientSalesObservedAt(LocalDateTime.now().minusDays(2));
        assertScoreAndCoverage(s, 15, 2);
        assertOneCode(s, 15, "UNVERIFIED_SELLER_HISTORY");
    }

    @Test
    void ceroVentasCompletadasVerificadasDa20() {
        Seller s = new Seller();
        s.setCompletedSales(0);
        s.setSalesObservedAt(LocalDateTime.now().minusHours(2));
        s.setListedProductsCount(3);
        s.setProductsObservedAt(LocalDateTime.now().minusHours(2));
        assertScoreAndCoverage(s, 20, 2);
        assertOneCode(s, 20, "ZERO_VERIFIED_SALES");
    }

    @Test
    void unaVentaVerificadaDa15() {
        Seller s = new Seller();
        s.setCompletedSales(1);
        s.setSalesObservedAt(LocalDateTime.now().minusHours(2));
        assertScoreAndCoverage(s, 15, 2);
        assertOneCode(s, 15, "LIMITED_SELLER_HISTORY");
    }

    @Test
    void cuatroVentasTienenPrioridadSobreCatalogoYMensaje() {
        Seller s = new Seller();
        s.setCompletedSales(4);
        s.setSalesObservedAt(LocalDateTime.now().minusDays(1));
        s.setListedProductsCount(3);
        s.setProductsObservedAt(LocalDateTime.now().minusDays(1));
        s.setInsufficientSalesForReputation(true);
        s.setInsufficientSalesObservedAt(LocalDateTime.now().minusDays(1));
        assertScoreAndCoverage(s, 15, 2);
        assertOneCode(s, 15, "LIMITED_SELLER_HISTORY");
    }

    @Test
    void cincoYNueveVentasDan5() {
        for (int sales : new int[] {5, 9}) {
            Seller s = new Seller();
            s.setCompletedSales(sales);
            s.setSalesObservedAt(LocalDateTime.now().minusDays(1));
            assertScoreAndCoverage(s, 5, 2);
            assertOneCode(s, 5, "LIMITED_SELLER_HISTORY");
        }
    }

    @Test
    void diezYDiecinueveVentasNoPenalizan() {
        for (int sales : new int[] {10, 19}) {
            Seller s = new Seller();
            s.setCompletedSales(sales);
            s.setSalesObservedAt(LocalDateTime.now().minusDays(1));
            assertScoreAndCoverage(s, 0, 2);
            assertTrue(engine.evaluate(publication(s)).getFactors().isEmpty());
        }
    }

    @Test
    void observacionCaducadaNoCuenta() {
        Seller s = new Seller();
        s.setListedProductsCount(3);
        s.setProductsObservedAt(LocalDateTime.now().minusDays(31));
        s.setSalesCountNotDisplayedObservedAt(LocalDateTime.now().minusDays(31));
        assertScoreAndCoverage(s, 0, 1);
        assertEquals(RiskLevel.INDETERMINATE, engine.evaluate(publication(s)).getLevel());
    }

    @Test
    void datosInaccesiblesNoSePenalizanNiCubren() {
        assertScoreAndCoverage(new Seller(), 0, 1);
    }

    @Test
    void reputacionConsolidadaImpidePenalizarAusenciaDeVentas() {
        Seller s = new Seller();
        s.setReputationLevel(4);
        s.setListedProductsCount(3);
        s.setProductsObservedAt(LocalDateTime.now().minusDays(1));
        s.setSalesCountNotDisplayedObservedAt(LocalDateTime.now().minusDays(1));
        assertScoreAndCoverage(s, 0, 2);
    }

    @Test
    void catalogoGrandeSinConstanciaDeBusquedaDeVentasNoBasta() {
        Seller s = new Seller();
        s.setListedProductsCount(20);
        s.setProductsObservedAt(LocalDateTime.now().minusDays(1));
        assertScoreAndCoverage(s, 0, 1);
    }

    @Test
    void datoDeVentasDesactualizadoYCatalogoRecienteDa15() {
        Seller s = new Seller();
        s.setCompletedSales(4);
        s.setSalesObservedAt(LocalDateTime.now().minusDays(31));
        s.setListedProductsCount(3);
        s.setProductsObservedAt(LocalDateTime.now().minusDays(1));
        assertScoreAndCoverage(s, 15, 2);
        assertOneCode(s, 15, "UNVERIFIED_SELLER_HISTORY");
    }

    @Test
    void cifraDeVentasSinFechaNoCuentaComoCeroNiComoVerificada() {
        Seller s = new Seller();
        s.setCompletedSales(0);
        assertScoreAndCoverage(s, 0, 1);
    }
}
