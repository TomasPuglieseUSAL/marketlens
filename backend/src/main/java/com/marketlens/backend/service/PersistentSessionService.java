package com.marketlens.backend.service;

import com.marketlens.backend.dto.PersistentSessionResponse;
import com.marketlens.backend.entity.RefreshSession;
import com.marketlens.backend.entity.User;
import com.marketlens.backend.repository.RefreshSessionRepository;
import com.marketlens.backend.repository.UserRepository;
import com.marketlens.backend.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class PersistentSessionService {
    private final SecureRandom random = new SecureRandom();
    private final RefreshSessionRepository sessions;
    private final UserRepository users;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final long accessSeconds;
    private final long inactivityDays;
    private final long maximumDays;

    public PersistentSessionService(
            RefreshSessionRepository sessions, UserRepository users,
            UserDetailsService userDetailsService, JwtService jwtService,
            @Value("${security.jwt.expiration}") long accessSeconds,
            @Value("${security.session.inactivity-days:90}") long inactivityDays,
            @Value("${security.session.maximum-days:365}") long maximumDays) {
        if (accessSeconds <= 0 || inactivityDays <= 0 || maximumDays < inactivityDays) {
            throw new IllegalArgumentException("Duraciones de sesión inválidas");
        }
        this.sessions = sessions;
        this.users = users;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.accessSeconds = accessSeconds;
        this.inactivityDays = inactivityDays;
        this.maximumDays = maximumDays;
    }

    /** Solo se crea una sesión persistente tras autenticar credenciales válidas. */
    @Transactional
    public PersistentSessionResponse issue(Authentication authentication) {
        User user = users.findByUsername(authentication.getName())
                .orElseThrow(this::unauthorized);
        Instant now = Instant.now();
        RefreshSession session = new RefreshSession();
        session.setUser(user);
        session.setCreatedAt(now);
        session.setLastUsedAt(now);
        session.setExpiresAt(now.plus(inactivityDays, ChronoUnit.DAYS));
        session.setAbsoluteExpiresAt(now.plus(maximumDays, ChronoUnit.DAYS));
        // El token incluye el ID generado: almacenar un hash provisional único.
        session.setTokenHash(hash(newSecret()));
        sessions.saveAndFlush(session);
        String refreshToken = newToken(session.getId());
        session.setTokenHash(hash(refreshToken));
        return response(jwtService.generateToken(authentication), refreshToken);
    }

    /**
     * Rotación atómica y período deslizante de inactividad (máximo absoluto).
     * La reutilización del secreto anterior revoca la sesión.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public PersistentSessionResponse refresh(String presentedToken) {
        RefreshSession session = findLocked(presentedToken)
                .orElseThrow(this::unauthorized);
        Instant now = Instant.now();
        String presentedHash = hash(presentedToken);
        if (!sameHash(presentedHash, session.getTokenHash())) {
            if (session.getRevokedAt() == null
                    && sameHash(presentedHash, session.getPreviousTokenHash())) {
                session.setRevokedAt(now);
            }
            throw unauthorized();
        }
        if (session.getRevokedAt() != null || !now.isBefore(session.getExpiresAt())
                || !now.isBefore(session.getAbsoluteExpiresAt())) {
            if (session.getRevokedAt() == null) session.setRevokedAt(now);
            throw unauthorized();
        }

        UserDetails details = userDetailsService.loadUserByUsername(
                session.getUser().getUsername());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                details, null, details.getAuthorities());
        String nextToken = newToken(session.getId());
        String accessToken = jwtService.generateToken(authentication);
        session.setPreviousTokenHash(session.getTokenHash());
        session.setTokenHash(hash(nextToken));
        session.setLastUsedAt(now);
        Instant slidingExpiry = now.plus(inactivityDays, ChronoUnit.DAYS);
        session.setExpiresAt(slidingExpiry.isBefore(session.getAbsoluteExpiresAt())
                ? slidingExpiry : session.getAbsoluteExpiresAt());
        return response(accessToken, nextToken);
    }

    /** Revoca la renovación; un JWT ya emitido vence según su propio exp. */
    @Transactional
    public void revoke(String presentedToken) {
        Optional<RefreshSession> found = findLocked(presentedToken);
        if (found.isEmpty()) return;
        RefreshSession session = found.get();
        String presentedHash = hash(presentedToken);
        if (sameHash(presentedHash, session.getTokenHash())
                || sameHash(presentedHash, session.getPreviousTokenHash())) {
            if (session.getRevokedAt() == null) session.setRevokedAt(Instant.now());
        }
    }

    private Optional<RefreshSession> findLocked(String token) {
        if (token == null || !token.matches("[1-9][0-9]{0,18}\\.[A-Za-z0-9_-]{43}")) {
            return Optional.empty();
        }
        try {
            return sessions.findForUpdate(Long.parseLong(
                    token.substring(0, token.indexOf('.'))));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private String newToken(long id) { return id + "." + newSecret(); }

    private String newSecret() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    private boolean sameHash(String expected, String actual) {
        return expected != null && actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    private PersistentSessionResponse response(String access, String refresh) {
        return new PersistentSessionResponse(access, "Bearer", accessSeconds, refresh);
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Sesión caducada o credencial de renovación inválida.");
    }
}
