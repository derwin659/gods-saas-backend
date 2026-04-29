package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.request.ResetPasswordRequest;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.model.PasswordResetToken;
import com.gods.saas.domain.repository.AppUserRepository;
import com.gods.saas.domain.repository.PasswordResetTokenRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final AppUserRepository appUserRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Transactional
    public void sendResetCode(String rawEmail) {
        if (rawEmail == null || rawEmail.trim().isEmpty()) {
            return;
        }

        String email = rawEmail.trim().toLowerCase();

        Optional<AppUser> userOpt = appUserRepository.findByEmail(email);

        // Importante: no revelar si el correo existe o no.
        if (userOpt.isEmpty()) {
            return;
        }

        AppUser user = userOpt.get();

        if (user.getActivo() != null && !user.getActivo()) {
            return;
        }

        String code = String.format("%06d", new Random().nextInt(1_000_000));

        PasswordResetToken token = PasswordResetToken.builder()
                .email(email)
                .codeHash(passwordEncoder.encode(code))
                .used(false)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();

        tokenRepository.save(token);

        emailService.sendPasswordResetCode(email, code);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correo inválido.");
        }

        if (request.getCode() == null || request.getCode().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código inválido.");
        }

        if (request.getNewPassword() == null || request.getNewPassword().trim().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 6 caracteres.");
        }

        String email = request.getEmail().trim().toLowerCase();
        String code = request.getCode().trim();

        PasswordResetToken token = tokenRepository
                .findTopByEmailAndUsedFalseOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Código inválido o expirado."
                ));

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El código ha expirado. Solicita uno nuevo."
            );
        }

        if (!passwordEncoder.matches(code, token.getCodeHash())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Código inválido o expirado."
            );
        }

        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Código inválido o expirado."
                ));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword().trim()));
        user.setFechaActualizacion(LocalDateTime.now());

        appUserRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);
    }
}