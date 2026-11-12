
package com.marketlens.backend.controller;

import com.marketlens.backend.dto.SellerRefreshResponse;
import com.marketlens.backend.service.SellerRefreshService;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/publications")
public class SellerRefreshController {

    private final SellerRefreshService service;

    public SellerRefreshController(SellerRefreshService service) {
        this.service = service;
    }

    @PostMapping("/{id}/refresh-seller")
    public SellerRefreshResponse refreshSeller(
            @PathVariable Long id,
            Authentication authentication) {

        // DEBUG 1: comprobar si la solicitud llega al controlador.
        System.out.println(
                "[DEBUG] Entró a SellerRefreshController. Publicación: " + id);

        if (authentication == null ||
                !authentication.isAuthenticated() ||
                authentication.getName() == null ||
                authentication.getName().isBlank() ||
                "anonymousUser".equals(authentication.getName())) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Se necesita una sesión autenticada.");
        }

        // DEBUG 2: comprobar que la autenticación fue aceptada.
        System.out.println(
                "[DEBUG] Usuario autenticado: " + authentication.getName());

        try {
            return service.refresh(id, authentication.getName());

        } catch (ResponseStatusException exception) {

            System.out.println(
                    "[DEBUG] SellerRefreshService devolvió HTTP "
                            + exception.getStatusCode().value());

            System.out.println(
                    "[DEBUG] Motivo: " + exception.getReason());

            throw exception;
        }
    }
}
