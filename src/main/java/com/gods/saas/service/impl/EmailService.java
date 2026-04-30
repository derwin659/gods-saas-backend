package com.gods.saas.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final RestTemplate restTemplate;

    @Value("${brevo.api.key}")
    private String brevoApiKey;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Value("${app.mail.from-name:Super Gods}")
    private String mailFromName;

    public void sendPasswordResetCode(String to, String code) {
        System.out.println("EMAIL SERVICE BREVO API => preparando correo");
        System.out.println("EMAIL SERVICE BREVO API => from=" + mailFrom);
        System.out.println("EMAIL SERVICE BREVO API => to=" + to);

        String url = "https://api.brevo.com/v3/smtp/email";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", brevoApiKey);

        Map<String, Object> body = Map.of(
                "sender", Map.of(
                        "name", mailFromName,
                        "email", mailFrom.trim()
                ),
                "to", List.of(
                        Map.of("email", to.trim())
                ),
                "subject", "Código para recuperar tu contraseña - App GODS",
                "htmlContent", """
                        <div style="font-family: Arial, sans-serif; color: #111827; line-height: 1.5;">
                          <h2>Recupera tu contraseña</h2>
                          <p>Hola,</p>
                          <p>Recibimos una solicitud para recuperar tu contraseña en GODS.</p>
                          <p>Tu código de recuperación es:</p>
                          <div style="font-size: 28px; font-weight: bold; letter-spacing: 4px; margin: 20px 0;">
                            %s
                          </div>
                          <p>Este código vence en 10 minutos.</p>
                          <p>Si no solicitaste este cambio, puedes ignorar este correo.</p>
                          <p>GODS</p>
                        </div>
                        """.formatted(code)
        );

        try {
            System.out.println("EMAIL SERVICE BREVO API => enviando request HTTP...");

            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            System.out.println("EMAIL SERVICE BREVO API => status=" + response.getStatusCode());
            System.out.println("EMAIL SERVICE BREVO API => body=" + response.getBody());

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Brevo no aceptó el correo. Status: " + response.getStatusCode());
            }

            System.out.println("EMAIL SERVICE BREVO API => correo enviado correctamente");

        } catch (Exception e) {
            System.out.println("EMAIL SERVICE BREVO API => ERROR");
            e.printStackTrace();
            throw new RuntimeException("No se pudo enviar el correo de recuperación por Brevo API", e);
        }
    }
}