package com.marketlens.backend.dto;

import com.marketlens.backend.entity.UserRole;

import java.time.LocalDateTime;

public record UserResponse(
    Long id,
    String username,
    String email,
    UserRole role,
    LocalDateTime createdAt
) {
}