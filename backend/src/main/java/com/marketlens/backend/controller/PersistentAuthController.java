package com.marketlens.backend.controller;

import com.marketlens.backend.dto.LoginRequest;
import com.marketlens.backend.dto.PersistentSessionResponse;
import com.marketlens.backend.service.PersistentSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class PersistentAuthController {
    private final AuthenticationManager authenticationManager;
    private final PersistentSessionService sessions;

    public PersistentAuthController(AuthenticationManager authenticationManager,
                                    PersistentSessionService sessions) {
        this.authenticationManager = authenticationManager;
        this.sessions = sessions;
    }

    // Endpoint exclusivo de la extensión; el login web no cambia.
    @PostMapping("/extension-login")
    public PersistentSessionResponse extensionLogin(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        return sessions.issue(authentication);
    }

    @PostMapping("/refresh")
    public PersistentSessionResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return sessions.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        sessions.revoke(request.refreshToken());
    }

    public record RefreshRequest(@NotBlank String refreshToken) {}
}
