package com.marketlens.backend.controller;

import com.marketlens.backend.dto.RegisterRequest;
import com.marketlens.backend.dto.UserResponse;
import com.marketlens.backend.dto.LoginRequest;
import com.marketlens.backend.dto.LoginResponse;
import com.marketlens.backend.service.AuthService;
import com.marketlens.backend.service.UserService;
import com.marketlens.backend.service.AuthService;


import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;
    
    public AuthController(
        UserService userService,
        AuthService authService) {
        
        this.userService = userService;
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(
            @Valid @RequestBody RegisterRequest request) {

        return userService.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(
        @Valid @RequestBody LoginRequest request) {
        
        return authService.login(request);
    }
}