package com.marketlens.backend.service;

import com.marketlens.backend.entity.RefreshSession;
import com.marketlens.backend.repository.RefreshSessionRepository;
import com.marketlens.backend.repository.UserRepository;
import com.marketlens.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Rotación, revocación y límites sin depender de un servidor HTTP. */
class PersistentSessionServiceTest {
    private RefreshSessionRepository sessions;
    private PersistentSessionService service;
    private AtomicReference<RefreshSession> current;

    @BeforeEach
    void setup() {
        sessions = mock(RefreshSessionRepository.class);
        var users = mock(UserRepository.class);
        var details = mock(UserDetailsService.class);
        var jwt = mock(JwtService.class);
        current = new AtomicReference<>();
        com.marketlens.backend.entity.User user = new com.marketlens.backend.entity.User();
        user.setUsername("demo");
        when(users.findByUsername("demo")).thenReturn(Optional.of(user));
        when(details.loadUserByUsername("demo")).thenReturn(
                User.withUsername("demo").password("unused").roles("USER").build());
        when(jwt.generateToken(any())).thenReturn("access-token");
        when(sessions.saveAndFlush(any())).thenAnswer(invocation -> {
            RefreshSession saved = invocation.getArgument(0);
            saved.setId(7L);
            current.set(saved);
            return saved;
        });
        when(sessions.findForUpdate(7L)).thenAnswer(invocation ->
                Optional.ofNullable(current.get()));
        service = new PersistentSessionService(sessions, users, details, jwt,
                3600, 90, 365);
    }

    private String issue() {
        return service.issue(new UsernamePasswordAuthenticationToken(
                "demo", "password")).refreshToken();
    }

    @Test
    void issueStoresOnlyHashAndRefreshRotates() {
        String original = issue();
        var rotated = service.refresh(original);
        assertEquals(3600, rotated.expiresIn());
        String next = rotated.refreshToken();
        String nextAgain = service.refresh(next).refreshToken();
        assertNotEquals(next, nextAgain);
        assertNotNull(next);
        assertNotEquals(original, next);
        assertFalse(current.get().getTokenHash().equals(original));
    }

    @Test
    void reuseOfPreviousTokenRevokesSession() {
        String original = issue();
        var renewed = service.refresh(original);
        assertNotEquals(original, renewed.refreshToken());
        assertThrows(ResponseStatusException.class, () -> service.refresh(original));
        assertNotNull(current.get().getRevokedAt());
        assertThrows(ResponseStatusException.class,
                () -> service.refresh(renewed.refreshToken()));
    }

    @Test
    void explicitLogoutRevokesOnlyPresentedSession() {
        String original = issue();
        service.revoke(original);
        assertNotNull(current.get().getRevokedAt());
        assertThrows(ResponseStatusException.class, () -> service.refresh(original));
        assertDoesNotThrow(() -> service.revoke(original));
    }

    @Test
    void expiredRefreshTokenCannotBeRenewed() {
        String original = issue();
        current.get().setExpiresAt(java.time.Instant.now().minusSeconds(1));
        assertThrows(ResponseStatusException.class, () -> service.refresh(original));
        assertNotNull(current.get().getRevokedAt());
    }
}
