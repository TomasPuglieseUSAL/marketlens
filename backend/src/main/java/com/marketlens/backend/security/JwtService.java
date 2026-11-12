package com.marketlens.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final long expirationSeconds;

    public JwtService(
            JwtEncoder jwtEncoder,
            @Value("${security.jwt.expiration}")
            long expirationSeconds) {

        this.jwtEncoder = jwtEncoder;
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(Authentication authentication) {

        Instant now = Instant.now();

        List<String> roles = authentication
            .getAuthorities()
            .stream()
            .map(GrantedAuthority::getAuthority)
            .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("marketlens")
            .issuedAt(now)
            .expiresAt(
                now.plus(expirationSeconds, ChronoUnit.SECONDS)
            )
            .subject(authentication.getName())
            .claim("roles", roles)
            .build();

        JwsHeader header = JwsHeader
            .with(MacAlgorithm.HS256)
            .build();

        return jwtEncoder
            .encode(
                JwtEncoderParameters.from(header, claims)
            )
            .getTokenValue();
    }
}