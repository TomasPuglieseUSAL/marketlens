package com.marketlens.backend.config;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/** Inserta publicaciones ficticias en los perfiles locales demo y registration. */
@Component
@Profile({"demo", "registration"})
@Order(20)
public class DemoFixturesInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoFixturesInitializer.class);
    private final DataSource dataSource;
    private final boolean enabled;

    public DemoFixturesInitializer(
            DataSource dataSource,
            @Value("${marketlens.demo.seed-samples:true}") boolean enabled) {
        this.dataSource = dataSource;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Datos de ejemplo deshabilitados por configuración.");
            return;
        }

        // INSERT ... ON CONFLICT DO NOTHING hace idempotente la carga.
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("demo/demo-fixtures.sql"));
        populator.execute(dataSource);
        log.info("Datos ficticios de MarketLens verificados en la base de demostración.");
    }
}
