package com.marketlens.backend.config;

import com.marketlens.backend.entity.User;
import com.marketlens.backend.entity.UserRole;
import com.marketlens.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DemoUserInitializerTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    @Test
    void createsUserWithEncodedPassword() {
        when(encoder.encode("SomeSecurePassword_123")).thenReturn("hashed-password");
        DemoUserInitializer runner = new DemoUserInitializer(
                users, encoder, "demo", "demo@marketlens.local", "SomeSecurePassword_123");

        runner.run(null);

        ArgumentCaptor<User> captured = ArgumentCaptor.forClass(User.class);
        verify(users).save(captured.capture());
        assertEquals("demo", captured.getValue().getUsername());
        assertEquals("demo@marketlens.local", captured.getValue().getEmail());
        assertEquals("hashed-password", captured.getValue().getPasswordHash());
        assertEquals(UserRole.USER, captured.getValue().getRole());
        verify(encoder).encode("SomeSecurePassword_123");
    }

    @Test
    void existingAccountIsNotModified() {
        when(users.existsByUsername("demo")).thenReturn(true);
        DemoUserInitializer runner = new DemoUserInitializer(
                users, encoder, "demo", "demo@marketlens.local", "SomeSecurePassword_123");
        runner.run(null);
        verify(users, never()).save(any());
        verifyNoInteractions(encoder);
    }

    @Test
    void missingPasswordCannotCreateAccount() {
        DemoUserInitializer runner = new DemoUserInitializer(
                users, encoder, "demo", "demo@marketlens.local", "");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> runner.run(null));
        assertTrue(e.getMessage().contains("DEMO_PASSWORD"));
        verify(users, never()).save(any());
    }

    @Test
    void duplicateEmailIsRejected() {
        when(users.existsByEmail("demo@marketlens.local")).thenReturn(true);
        DemoUserInitializer runner = new DemoUserInitializer(
                users, encoder, "demo", "demo@marketlens.local", "SomeSecurePassword_123");
        assertThrows(IllegalStateException.class, () -> runner.run(null));
        verify(users, never()).save(any());
    }
}
