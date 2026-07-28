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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class OwnerWhatsappPhoneVerificationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_EXPIRY_MINUTES = 10;
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 5;
    private static final Pattern INBOUND_CODE_PATTERN = Pattern.compile(
            "(?i)^\\s*VERIFICAR\\s+GODS\\s+(\\d+)\\s+(\\d{6})\\s*$");

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final WhatsappRecipientResolver recipientResolver;
    private final CentralWhatsappSenderService centralWhatsappSenderService;

    @Transactional(readOnly = true)
    public OwnerWhatsappVerificationResponse getStatus(Long tenantId, Long userId) {
        return response(requireUser(tenantId, userId), null);
    }

    @Transactional
    public OwnerWhatsappVerificationResponse requestCode(Long tenantId, Long userId, String rawPhone) {
        if (!centralWhatsappSenderService.isInboundVerificationConfigured()) {
            throw new RuntimeException(
                    "La verificacion entrante de GODS Notificaciones aun no esta habilitada."
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

        return response(user, code);
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

        return response(user, null);
    }

    @Transactional
    public InboundVerificationResult verifyInbound(String rawFrom, String rawBody) {
        String phone = normalizeInboundPhone(rawFrom);
        Matcher matcher = INBOUND_CODE_PATTERN.matcher(rawBody == null ? "" : rawBody);
        if (phone == null || !matcher.matches()) {
            return new InboundVerificationResult(
                    false,
                    "No pude validar el mensaje. Abre nuevamente GODS y genera otro enlace de verificacion."
            );
        }

        Long userId;
        try {
            userId = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return new InboundVerificationResult(false, "El enlace de verificacion no es valido.");
        }
        String code = matcher.group(2);
        AppUser user = appUserRepository.findById(userId).orElse(null);
        LocalDateTime now = LocalDateTime.now();

        if (user == null
                || user.getWhatsappPendingPhone() == null
                || user.getWhatsappVerificationCodeHash() == null
                || user.getWhatsappVerificationExpiresAt() == null) {
            return new InboundVerificationResult(
                    false,
                    "No existe una verificacion pendiente. Genera un enlace nuevo desde GODS."
            );
        }
        if (!phone.equals(user.getWhatsappPendingPhone())) {
            return new InboundVerificationResult(
                    false,
                    "Este enlace corresponde a otro numero. En GODS registra el WhatsApp desde el que escribes."
            );
        }
        if (user.getWhatsappVerificationExpiresAt().isBefore(now)) {
            clearPending(user);
            appUserRepository.save(user);
            return new InboundVerificationResult(
                    false,
                    "El enlace vencio. Genera uno nuevo desde GODS."
            );
        }

        int attempts = safeAttempts(user) + 1;
        if (!passwordEncoder.matches(code, user.getWhatsappVerificationCodeHash())) {
            user.setWhatsappVerificationAttempts(attempts);
            if (attempts >= MAX_ATTEMPTS) {
                clearPending(user);
            }
            appUserRepository.save(user);
            return new InboundVerificationResult(
                    false,
                    attempts >= MAX_ATTEMPTS
                            ? "El enlace ya no es valido. Genera uno nuevo desde GODS."
                            : "El codigo no coincide. Usa el mensaje generado directamente por GODS."
            );
        }

        user.setPhone(phone);
        user.setWhatsappVerifiedPhone(phone);
        user.setWhatsappPhoneVerifiedAt(now);
        user.setFechaActualizacion(now);
        clearPending(user);
        appUserRepository.save(user);

        return new InboundVerificationResult(
                true,
                "WhatsApp verificado correctamente. Regresa a GODS y activa las alertas de reservas."
        );
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

    private OwnerWhatsappVerificationResponse response(AppUser user, String code) {
        boolean verified = isVerifiedRecipient(user);
        LocalDateTime now = LocalDateTime.now();
        boolean pending = user.getWhatsappPendingPhone() != null
                && user.getWhatsappVerificationCodeHash() != null
                && user.getWhatsappVerificationExpiresAt() != null
                && user.getWhatsappVerificationExpiresAt().isAfter(now);
        LocalDateTime canRequestAt = user.getWhatsappVerificationRequestedAt() == null
                ? null
                : user.getWhatsappVerificationRequestedAt().plusSeconds(RESEND_COOLDOWN_SECONDS);
        String verificationMessage = code == null
                ? null
                : centralWhatsappSenderService.verificationMessage(user.getId(), code);

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
                .verificationMode("INBOUND_WHATSAPP")
                .verificationMessage(verificationMessage)
                .verificationUrl(code == null
                        ? null
                        : centralWhatsappSenderService.verificationChatUrl(user.getId(), code))
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

    private String normalizeInboundPhone(String value) {
        if (value == null || value.isBlank()) return null;
        String digits = value.replace("whatsapp:", "").replaceAll("[^0-9]", "");
        if (digits.length() < 8 || digits.length() > 15) return null;
        return "+" + digits;
    }

    public record InboundVerificationResult(boolean verified, String reply) {
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