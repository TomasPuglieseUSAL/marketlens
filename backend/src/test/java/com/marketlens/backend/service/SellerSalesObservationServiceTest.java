package com.marketlens.backend.service;

import com.marketlens.backend.dto.SellerSalesObservationRequest;
import com.marketlens.backend.dto.SellerSalesObservationResponse;
import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.RiskEvaluation;
import com.marketlens.backend.entity.Seller;
import com.marketlens.backend.repository.PublicationRepository;
import com.marketlens.backend.repository.SellerRepository;
import com.marketlens.backend.risk.RiskEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SellerSalesObservationServiceTest {

    private PublicationRepository publicationRepository;
    private SellerRepository sellerRepository;
    private SellerSalesObservationService service;
    private Publication publication;
    private Seller seller;
    private final RiskEngine engine = new RiskEngine(new RiskCoverageCalculator());

    @BeforeEach
    void setUp() {
        publicationRepository = mock(PublicationRepository.class);
        sellerRepository = mock(SellerRepository.class);
        service = new SellerSalesObservationService(publicationRepository, sellerRepository);
        seller = new Seller();
        seller.setName("SEBLONDIERIOS");
        seller.setMarketplace(Marketplace.MERCADO_LIBRE);
        publication = new Publication();
        publication.setMarketplace(Marketplace.MERCADO_LIBRE);
        publication.setExternalId("MLAU5481893051");
        publication.setDescription("Consola usada en buen estado.");
        publication.setSeller(seller);
        when(publicationRepository.findById(27L)).thenReturn(Optional.of(publication));
    }

    private SellerSalesObservationRequest request(
            String sellerName, String sales, String products, String insufficient) {
        return new SellerSalesObservationRequest("MERCADO_LIBRE", "MLAU5481893051",
                sellerName, sales, products, insufficient);
    }

    private void assertRisk(int points, int coverage) {
        RiskEvaluation evaluation = engine.evaluate(publication);
        assertEquals(points, (int) evaluation.getScore());
        assertEquals(coverage, (int) evaluation.getAvailableFactors());
    }

    @Test
    void tresProductosCubrenReputacionConQuincePuntos() {
        SellerSalesObservationResponse response = service.observe(27L,
                request("SEBLONDIERIOS", null, "3 productos", null));
        assertEquals("ACTIVITY_UPDATED", response.status());
        assertEquals(3, (int) seller.getListedProductsCount());
        assertNotNull(seller.getProductsObservedAt());
        assertNull(seller.getCompletedSales());
        assertRisk(15, 2);
        verify(sellerRepository).save(seller);
    }

    @Test
    void nombreConTresProductosTodaviaCoincideConVendedor() {
        SellerSalesObservationResponse response = service.observe(27L,
                request("Vendido por SEBLONDIERIOS, 3 productos", null, "3 productos", null));
        assertEquals("ACTIVITY_UPDATED", response.status());
        assertRisk(15, 2);
    }

    @Test
    void declaracionExplicitaDeVentasInsuficientesDaQuincePuntos() {
        SellerSalesObservationResponse response = service.observe(27L,
                request("SEBLONDIERIOS", null, null,
                        "No tiene suficientes ventas para evaluar su atención"));
        assertEquals("ACTIVITY_UPDATED", response.status());
        assertTrue(seller.getInsufficientSalesForReputation());
        assertNotNull(seller.getInsufficientSalesObservedAt());
        assertRisk(15, 2);
    }

    @Test
    void cuatroVentasTienenPrioridadSobreCatalogoYPenalizanQuince() {
        SellerSalesObservationResponse response = service.observe(27L,
                request("SEBLONDIERIOS", "4 ventas", "3 productos",
                        "No tiene suficientes ventas para evaluar su atención"));
        assertEquals("UPDATED", response.status());
        assertEquals(4, (int) seller.getCompletedSales());
        assertNull(seller.getInsufficientSalesForReputation());
        assertRisk(15, 2);
    }

    @Test
    void conservarObservacionExactaAnteriorAnteVentasAproximadas() {
        seller.setCompletedSales(4);
        LocalDateTime past = LocalDateTime.now().minusDays(2);
        seller.setSalesObservedAt(past);
        SellerSalesObservationResponse response = service.observe(27L,
                request("SEBLONDIERIOS", "+50 ventas", null, null));
        assertEquals("APPROXIMATE_SALES", response.status());
        assertEquals(4, (int) seller.getCompletedSales());
        assertEquals(past, seller.getSalesObservedAt());
        verify(sellerRepository, never()).save(any());
    }

    @Test
    void sinDatosNoInventaObservacionNiCobertura() {
        SellerSalesObservationResponse response = service.observe(27L,
                request("SEBLONDIERIOS", null, null, null));
        assertEquals("NO_EXACT_SALES", response.status());
        assertNull(seller.getListedProductsCount());
        assertNull(seller.getSalesCountNotDisplayedObservedAt());
        assertRisk(0, 1);
        verify(sellerRepository, never()).save(any());
    }

    @Test
    void ignorarCantidadAproximadaDeProductos() {
        service.observe(27L, request("SEBLONDIERIOS", null, "+3 productos", null));
        assertNull(seller.getListedProductsCount());
        assertRisk(0, 1);
    }

    @Test
    void nombreDeOtroVendedorNoPuedeActualizar() {
        SellerSalesObservationResponse response = service.observe(27L,
                request("VENDEDOR_DISTINTO", null, "3 productos", null));
        assertEquals("SELLER_MISMATCH", response.status());
        assertNull(seller.getListedProductsCount());
        verify(sellerRepository, never()).save(any());
    }

    @Test
    void idDeOtraPublicacionEsRechazado() {
        var request = new SellerSalesObservationRequest("MERCADO_LIBRE",
                "MLAU9999999999", "SEBLONDIERIOS", null, "3 productos", null);
        assertThrows(ResponseStatusException.class, () -> service.observe(27L, request));
        verify(sellerRepository, never()).save(any());
    }

    @Test
    void nuevoDatoParcialNoBorraVentasPrevias() {
        seller.setCompletedSales(10);
        seller.setSalesObservedAt(LocalDateTime.now().minusDays(1));
        service.observe(27L, request("SEBLONDIERIOS", null, "3 productos", null));
        assertEquals(10, (int) seller.getCompletedSales());
        assertRisk(0, 2);
    }
}
