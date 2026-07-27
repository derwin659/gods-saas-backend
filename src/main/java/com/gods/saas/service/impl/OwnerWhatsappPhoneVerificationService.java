package com.gods.saas.service.impl;

import com.gods.saas.domain.dto.response.OwnerWhatsappVerificationResponse;
import com.gods.saas.domain.model.AppUser;
import com.gods.saas.domain.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OwnerWhatsappPhoneVerificationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_EXPIRY_MINUTES = 10;
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 5;

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final WhatsappRecipientResolver recipientResolver;
    private final CentralWhatsappSenderService centralWhatsappSenderService;

    @Transactional(readOnly = true)
    public OwnerWhatsappVerificationResponse getStatus(Long tenantId, Long userId) {
        return response(requireUser(tenantId, userId));
    }

    @Transactional
    public OwnerWhatsappVerificationResponse requestCode(Long tenantId, Long userId, String rawPhone) {
        if (!centralWhatsappSenderService.isConfigured()) {
            throw new RuntimeException(
                    "GODS Notificaciones aun no esta habilitado. Configura primero el proveedor central."
            );
        }

        AppUser user = requireUser(tenantId, userId);
        String digits = recipientResolver.normalizeDigits(rawPhone, user.getTenant());
        validatePhone(digits);
        String phone = "+" + digits;
        LocalDateTime now = LocalDateTime.now();

        if (user.getWhatsappVerificationRequestedAt() != null) {
            LocalDateTime nextRequest = user.getWhatsappVerificationRequestedAt()
                    .plusSeconds(RESEND_COOLDOWN_SECONDS);
            if (nextRequest.isAfter(now)) {
                long seconds = Math.max(1, java.time.Duration.between(now, nextRequest).getSeconds());
                throw new RuntimeException("Espera " + seconds + " segundos antes de solicitar otro codigo.");
            }
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        user.setWhatsappPendingPhone(phone);
        user.setWhatsappVerificationCodeHash(passwordEncoder.encode(code));
        user.setWhatsappVerificationExpiresAt(now.plusMinutes(CODE_EXPIRY_MINUTES));
        user.setWhatsappVerificationRequestedAt(now);
        user.setWhatsappVerificationAttempts(0);
        user.setFechaActualizacion(now);
        appUserRepository.saveAndFlush(user);

        centralWhatsappSenderService.sendVerification(
                user.getTenant(),
                digits,
                code,
                CODE_EXPIRY_MINUTES
        );

        return response(user);
    }

    @Transactional(noRollbackFor = VerificationCodeException.class)
    public OwnerWhatsappVerificationResponse verifyCode(Long tenantId, Long userId, String rawCode) {
        AppUser user = requireUser(tenantId, userId);
        String code = rawCode == null ? "" : rawCode.trim();
        LocalDateTime now = LocalDateTime.now();

        if (!code.matches("\\d{6}")) {
            throw new VerificationCodeException("Ingresa el codigo de 6 digitos.");
        }
        if (user.getWhatsappPendingPhone() == null
                || user.getWhatsappVerificationCodeHash() == null
                || user.getWhatsappVerificationExpiresAt() == null) {
            throw new VerificationCodeException("Solicita primero un nuevo codigo de verificacion.");
        }
        if (user.getWhatsappVerificationExpiresAt().isBefore(now)) {
            clearPending(user);
            appUserRepository.save(user);
            throw new VerificationCodeException("El codigo vencio. Solicita uno nuevo.");
        }

        int attempts = safeAttempts(user) + 1;
        if (!passwordEncoder.matches(code, user.getWhatsappVerificationCodeHash())) {
            user.setWhatsappVerificationAttempts(attempts);
            if (attempts >= MAX_ATTEMPTS) {
                clearPending(user);
                appUserRepository.save(user);
                throw new VerificationCodeException(
                        "Superaste los intentos permitidos. Solicita un codigo nuevo."
                );
            }
            appUserRepository.save(user);
            throw new VerificationCodeException(
                    "Codigo incorrecto. Te quedan " + (MAX_ATTEMPTS - attempts) + " intentos."
            );
        }

        String verifiedPhone = user.getWhatsappPendingPhone();
        user.setPhone(verifiedPhone);
        user.setWhatsappVerifiedPhone(verifiedPhone);
        user.setWhatsappPhoneVerifiedAt(now);
        user.setFechaActualizacion(now);
        clearPending(user);
        appUserRepository.save(user);

        return response(user);
    }

    @Transactional(readOnly = true)
    public boolean isVerifiedRecipient(AppUser user) {
        if (user == null
                || user.getTenant() == null
                || user.getWhatsappPhoneVerifiedAt() == null
                || user.getWhatsappVerifiedPhone() == null) {
            return false;
        }

        String currentDigits = recipientResolver.normalizeDigits(user.getPhone(), user.getTenant());
        if (currentDigits == null) return false;
        return ("+" + currentDigits).equals(user.getWhatsappVerifiedPhone());
    }

    public boolean isCentralReady() {
        return centralWhatsappSenderService.isConfigured();
    }

    private OwnerWhatsappVerificationResponse response(AppUser user) {
        boolean verified = isVerifiedRecipient(user);
        LocalDateTime now = LocalDateTime.now();
        boolean pending = user.getWhatsappPendingPhone() != null
                && user.getWhatsappVerificationCodeHash() != null
                && user.getWhatsappVerificationExpiresAt() != null
                && user.getWhatsappVerificationExpiresAt().isAfter(now);
        LocalDateTime canRequestAt = user.getWhatsappVerificationRequestedAt() == null
                ? null
                : user.getWhatsappVerificationRequestedAt().plusSeconds(RESEND_COOLDOWN_SECONDS);

        return OwnerWhatsappVerificationResponse.builder()
                .phone(user.getPhone())
                .maskedPhone(mask(user.getPhone()))
                .verified(verified)
                .verifiedAt(verified ? user.getWhatsappPhoneVerifiedAt() : null)
                .pendingPhone(pending ? user.getWhatsappPendingPhone() : null)
                .maskedPendingPhone(pending ? mask(user.getWhatsappPendingPhone()) : null)
                .codeExpiresAt(pending ? user.getWhatsappVerificationExpiresAt() : null)
                .canRequestAt(canRequestAt)
                .verificationPending(pending)
                .centralNotificationsEnabled(centralWhatsappSenderService.isConfigured())
                .centralProvider(centralWhatsappSenderService.provider())
                .centralSenderLabel(centralWhatsappSenderService.senderLabel())
                .build();
    }

    private AppUser requireUser(Long tenantId, Long userId) {
        return appUserRepository.findByIdAndTenant_Id(userId, tenantId)
                .orElseThrow(() -> new RuntimeException("Usuario owner no encontrado en el negocio."));
    }

    private void validatePhone(String digits) {
        if (digits == null || digits.length() < 8 || digits.length() > 15) {
            throw new RuntimeException(
                    "Ingresa un WhatsApp valido con codigo de pais, por ejemplo +51987654321."
            );
        }
    }

    private int safeAttempts(AppUser user) {
        return user.getWhatsappVerificationAttempts() == null
                ? 0
                : user.getWhatsappVerificationAttempts();
    }

    private void clearPending(AppUser user) {
        user.setWhatsappPendingPhone(null);
        user.setWhatsappVerificationCodeHash(null);
        user.setWhatsappVerificationExpiresAt(null);
        user.setWhatsappVerificationAttempts(0);
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) return "Sin numero";
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() <= 4) return "****";
        String prefix = digits.length() > 9 ? "+" + digits.substring(0, digits.length() - 9) + " " : "";
        return prefix + "*****" + digits.substring(digits.length() - 4);
    }

    public static class VerificationCodeException extends RuntimeException {
        public VerificationCodeException(String message) {
            super(message);
        }
    }
}