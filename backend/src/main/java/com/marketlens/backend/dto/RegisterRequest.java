package com.marketlens.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

    @NotBlank(message = "Username is required")
    @Size(
        min = 3,
        max = 100,
        message = "Username must contain between 3 and 100 characters"
    )
    String username,

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email,

    @NotBlank(message = "Password is required")
    @Size(
        min = 8,
        max = 100,
        message = "Password must contain between 8 and 100 characters"
    )
    String password

) {
}