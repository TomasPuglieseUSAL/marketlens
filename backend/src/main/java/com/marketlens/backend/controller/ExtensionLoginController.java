package com.marketlens.backend.controller;

import com.marketlens.backend.service.ExtensionLoginService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class ExtensionLoginController {

    private final ExtensionLoginService extensionLoginService;

    public ExtensionLoginController(ExtensionLoginService extensionLoginService) {
        this.extensionLoginService = extensionLoginService;
    }

    // Solo para extensiones que ya presentan un JWT válido.
    @PostMapping("/extension-link")
    public ExtensionLoginService.LinkResponse link(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Se necesita una sesión autenticada.");
        }
        return extensionLoginService.createLink(jwt);
    }

    // Público para permitir acceder al frontend sin sesión previa.
    // El secreto es el código aleatorio de un solo uso, no la contraseña.
    @PostMapping("/extension-exchange")
    public ExtensionLoginService.SessionResponse exchange(
            @RequestBody ExchangeRequest request) {
        return extensionLoginService.exchange(request.code());
    }

    public record ExchangeRequest(String code) {}
}
