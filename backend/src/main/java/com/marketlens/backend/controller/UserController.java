package com.marketlens.backend.controller;

import com.marketlens.backend.dto.UserResponse;
import com.marketlens.backend.service.UserService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Long id) {
        return userService.getById(id);
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser(
        Authentication authentication) {
        
        return userService.getByUsername(
            authentication.getName()
        );
    }
}