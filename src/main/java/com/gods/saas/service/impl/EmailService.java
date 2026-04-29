package com.gods.saas.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendPasswordResetCode(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Código para recuperar tu contraseña - App GODS");
        message.setText("""
                Hola,

                Recibimos una solicitud para recuperar tu contraseña en GODS.

                Tu código de recuperación es:

                %s

                Este código vence en 10 minutos.

                Si no solicitaste este cambio, puedes ignorar este correo.

                GODS
                """.formatted(code));

        mailSender.send(message);
    }
}