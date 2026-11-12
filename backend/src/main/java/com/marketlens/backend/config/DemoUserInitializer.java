package com.marketlens.backend.config;

import com.marketlens.backend.entity.User;
import com.marketlens.backend.entity.UserRole;
import com.marketlens.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Inicializa una cuenta local de demostración, solo con el perfil demo.
 * No altera usuarios existentes ni incluye contraseñas en el repositorio.
 */
@Component
@Profile("demo")
@Order(10)
public class DemoUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserInitializer.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String email;
    private final String password;

    public DemoUserInitializer(
            UserRepository users,
            PasswordEncoder encoder,
            @Value("${marketlens.demo.username:demo}") String username,
            @Value("${marketlens.demo.email:demo@marketlens.local}") String email,
            @Value("${marketlens.demo.password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username == null || username.isBlank() || email == null || email.isBlank()) {
            throw new IllegalStateException("Nombre/correo del usuario demo inválidos.");
        }

        // El usuario puede existir si ya se ejecutó el perfil demo.
        // Nunca se sobreescribe un passwordHash existente en cada reinicio.
        if (users.existsByUsername(username)) {
            log.info("Usuario de demostración '{}' ya existe; se conserva sin cambios.", username);
            return;
        }

        if (password == null || password.length() < 12) {
            throw new IllegalStateException(
                    "Falta DEMO_PASSWORD o es demasiado corta (mínimo 12 caracteres). " +
                    "Ejecutá scripts/start-demo.ps1 desde la raíz del proyecto.");
        }

        if (users.existsByEmail(email)) {
            throw new IllegalStateException(
                    "El correo configurado para la cuenta demo ya pertenece a otro usuario.");
        }

        User demo = new User();
        demo.setUsername(username);
        demo.setEmail(email);
        demo.setRole(UserRole.USER);
        demo.setPasswordHash(encoder.encode(password));
        users.save(demo);

        log.info("Usuario de demostración '{}' creado con rol USER.", username);
    }
}
