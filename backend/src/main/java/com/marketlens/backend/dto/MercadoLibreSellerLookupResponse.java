package com.marketlens.backend.dto;

public record MercadoLibreSellerLookupResponse(
    String searchedNickname,
    Long sellerId,
    String returnedNickname
) {
}