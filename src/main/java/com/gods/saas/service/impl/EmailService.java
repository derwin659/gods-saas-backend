package com.gods.saas.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String mailFrom;

    public void sendPasswordResetCode(String to, String code) {
        System.out.println("EMAIL SERVICE => preparando correo");
        System.out.println("EMAIL SERVICE => from=" + mailFrom);
        System.out.println("EMAIL SERVICE => to=" + to);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
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

            System.out.println("EMAIL SERVICE => enviando con JavaMailSender...");
            mailSender.send(message);
            System.out.println("EMAIL SERVICE => correo enviado correctamente");

        } catch (MailException e) {
            System.out.println("EMAIL SERVICE => ERROR MAIL");
            e.printStackTrace();
            throw e;
        } catch (Exception e) {
            System.out.println("EMAIL SERVICE => ERROR GENERAL");
            e.printStackTrace();
            throw new RuntimeException("No se pudo enviar el correo de recuperación", e);
        }
    }
}