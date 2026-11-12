package com.marketlens.backend.dto;

public record LoginResponse(
    String token,
    String tokenType,
    long expiresIn
) {
}