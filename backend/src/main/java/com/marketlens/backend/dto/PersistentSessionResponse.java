package com.marketlens.backend.dto;

/** Respuesta exclusiva de la extensión: no cambia el login web existente. */
public record PersistentSessionResponse(
        String token, String tokenType, long expiresIn, String refreshToken) {}
