package com.marketlens.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExtensionLoginService {

    private static final long CODE_TTL_SECONDS = 45;
    private static final int MAX_PENDING_CODES = 500;

    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, PendingTransfer> transfers =
            new ConcurrentHashMap<>();
    private final JwtDecoder jwtDecoder;

    public ExtensionLoginService(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    public record LinkResponse(String code, long expiresIn) {}
    public record SessionResponse(String token, long expiresIn) {}
    private record PendingTransfer(String token, Instant deadline) {}

    public LinkResponse createLink(Jwt jwt) {
        Instant now = Instant.now();
        Instant tokenExpiry = jwt.getExpiresAt();
        if (tokenExpiry == null || !tokenExpiry.isAfter(now.plusSeconds(5))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "La sesión de la extensión está por expirar.");
        }

        pruneExpired(now);
        if (transfers.size() >= MAX_PENDING_CODES) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados enlaces pendientes. Reintentá después.");
        }

        Instant deadline = now.plusSeconds(CODE_TTL_SECONDS);
        if (tokenExpiry.isBefore(deadline)) deadline = tokenExpiry;

        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String code = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(randomBytes);
        transfers.put(hash(code), new PendingTransfer(jwt.getTokenValue(), deadline));

        return new LinkResponse(code, Duration.between(now, deadline).getSeconds());
    }

    public SessionResponse exchange(String code) {
        if (code == null || !code.matches("[A-Za-z0-9_-]{43}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Código de acceso inválido.");
        }

        // remove es atómico: un código solo puede consumirse una vez.
        PendingTransfer transfer = transfers.remove(hash(code));
        if (transfer == null || !transfer.deadline().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "El código expiró o ya fue utilizado.");
        }

        try {
            // No confiamos solo en el registro temporal: volvemos a validar
            // firma y expiración usando el decodificador actual de Spring.
            Jwt jwt = jwtDecoder.decode(transfer.token());
            Instant expiry = jwt.getExpiresAt();
            if (expiry == null) {
                throw new ResponseStatusException(HttpStatus.GONE,
                        "La sesión ya no es válida.");
            }
            long remaining = Duration.between(Instant.now(), expiry).getSeconds();
            if (remaining <= 0) {
                throw new ResponseStatusException(HttpStatus.GONE,
                        "La sesión expiró.");
            }
            return new SessionResponse(transfer.token(), remaining);
        } catch (JwtException ex) {
            throw new ResponseStatusException(HttpStatus.GONE,
                    "La sesión expiró o dejó de ser válida.");
        }
    }

    private void pruneExpired(Instant now) {
        transfers.entrySet().removeIf(entry ->
                !entry.getValue().deadline().isAfter(now));
    }

    private String hash(String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(code.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
