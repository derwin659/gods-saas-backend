package com.gods.saas.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class FirebaseConfig {

    private final FirebaseProperties firebaseProperties;
    private final Environment environment;

    @PostConstruct
    public void init() {
        if (!firebaseProperties.isEnabled()) {
            log.info("Firebase disabled by config");
            return;
        }

        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                log.info("Firebase already initialized");
                return;
            }

            String firebaseJson = environment.getProperty("FIREBASE_SERVICE_ACCOUNT_JSON");

            InputStream credentialsStream;

            if (firebaseJson != null && !firebaseJson.isBlank()) {
                log.info("Initializing Firebase from FIREBASE_SERVICE_ACCOUNT_JSON");
                credentialsStream = new ByteArrayInputStream(
                        firebaseJson.getBytes(StandardCharsets.UTF_8)
                );
            } else if (firebaseProperties.getServiceAccountPath() != null
                    && !firebaseProperties.getServiceAccountPath().isBlank()) {
                log.info("Initializing Firebase from file path");
                credentialsStream = new FileInputStream(firebaseProperties.getServiceAccountPath());
            } else {
                throw new IllegalStateException(
                        "No Firebase credentials provided. Set FIREBASE_SERVICE_ACCOUNT_JSON or firebase.service-account-path"
                );
            }

            try (InputStream serviceAccount = credentialsStream) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("Firebase initialized successfully");
            }

        } catch (Exception e) {
            log.error("Error initializing Firebase", e);
        }
    }
}