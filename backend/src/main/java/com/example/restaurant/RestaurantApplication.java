package com.example.restaurant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
@EnableJpaAuditing
public class RestaurantApplication {

    private static final Logger log = LoggerFactory.getLogger(RestaurantApplication.class);

    public static void main(String[] args) {
        loadDotEnvIfPresent();
        SpringApplication.run(RestaurantApplication.class, args);
    }

    /**
     * Inspects current and parent paths for a .env file and sets non-empty
     * key-value pairs as JVM system properties so Spring Boot placeholders
     * such as ${STRIPE_SECRET_KEY} resolve seamlessly during local development.
     */
    private static void loadDotEnvIfPresent() {
        Path[] candidatePaths = new Path[]{
                Paths.get(".env"),
                Paths.get("../.env"),
                Paths.get("backend/.env")
        };

        for (Path path : candidatePaths) {
            if (Files.exists(path) && Files.isRegularFile(path)) {
                log.info("Loading environment configuration from: {}", path.toAbsolutePath().normalize());
                try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        int separatorIndex = line.indexOf('=');
                        if (separatorIndex > 0) {
                            String key = line.substring(0, separatorIndex).trim();
                            String value = line.substring(separatorIndex + 1).trim();

                            // Strip wrapping single or double quotes
                            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                                (value.startsWith("'") && value.endsWith("'"))) {
                                value = value.substring(1, value.length() - 1);
                            }

                            // Do not override environment variables already set by the OS / container
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, value);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Could not read .env file at {}: {}", path, e.getMessage());
                }
                break; // Stop after loading the first matching .env file
            }
        }
    }
}
