package com.marketlens.backend.service;

import com.marketlens.backend.dto.RegisterRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.marketlens.backend.dto.UserResponse;
import com.marketlens.backend.entity.User;
import com.marketlens.backend.entity.UserRole;
import com.marketlens.backend.exception.ResourceNotFoundException;
import com.marketlens.backend.exception.ResourceConflictException;
import com.marketlens.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder) {
            
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {

    if (userRepository.existsByUsername(request.username())) {
        throw new ResourceConflictException(
            "Username is already in use"
        );
    }

    if (userRepository.existsByEmail(request.email())) {
        throw new ResourceConflictException(
            "Email is already in use"
        );
    }

    User user = new User();

    user.setUsername(request.username());
    user.setEmail(request.email());

    user.setPasswordHash(
        passwordEncoder.encode(request.password())
    );

    user.setRole(UserRole.USER);

    User savedUser = userRepository.save(user);

    return toResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {

        User user = userRepository
            .findById(id)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "User not found: " + id
                )
            );

        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getByUsername(String username) {
        
        User user = userRepository
        .findByUsername(username)
        .orElseThrow(() ->
            new ResourceNotFoundException(
                "User not found: " + username
            )
        );
        
        return toResponse(user);
    }

    private UserResponse toResponse(User user) {

        return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getRole(),
            user.getCreatedAt()
        );
    }
}