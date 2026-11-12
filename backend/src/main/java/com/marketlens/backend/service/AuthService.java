package com.marketlens.backend.service;

import com.marketlens.backend.dto.LoginRequest;
import com.marketlens.backend.dto.LoginResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import com.marketlens.backend.security.JwtService;
import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Value;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Value("${security.jwt.expiration}")
    private long jwtExpirationSeconds;
    
    public AuthService(
        AuthenticationManager authenticationManager,
        JwtService jwtService) {
            
            this.authenticationManager = authenticationManager;
            this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        
        Authentication authentication =
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.username(),
                request.password()
            )
        );
        
        String token =
        jwtService.generateToken(authentication);
        
        return new LoginResponse(
            token,
            "Bearer",
            jwtExpirationSeconds
        );
        }
}