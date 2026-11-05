package com.gods.saas.service.impl;

import java.util.Base64;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingDepositEvidenceService {
    private static final int MAX_BYTES = 3 * 1024 * 1024;
    private static final Pattern DATA = Pattern.compile("^data:(image/(?:jpeg|png|webp));base64,");
    private final CloudinaryStorageService storage;

    public String prepare(Long tenantId, Long customerId, String value) {
        if (value == null || value.isBlank()) return null;
        String input = value.trim();
        // Existing uploaded URLs are preserved; never fetch arbitrary URLs here.
        if (!input.startsWith("data:")) return input;
        byte[] bytes = decode(input);
        var uploaded = storage.uploadAppointmentDepositEvidenceBytes(tenantId, customerId, bytes);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        try { storage.deleteImage(uploaded.getPublicId()); }
                        catch (Exception e) { log.warn("No se pudo retirar comprobante de reserva revertida: {}", uploaded.getPublicId()); }
                    }
                }
            });
        }
        return uploaded.getSecureUrl();
    }

    static byte[] decode(String input) {
        var match = DATA.matcher(input);
        if (!match.find() || input.length() > 4 * ((MAX_BYTES + 2) / 3) + 100)
            throw new IllegalArgumentException("Adjunta una imagen JPG, PNG o WEBP de hasta 3 MB");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(input.substring(match.end())); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("El comprobante no es una imagen valida"); }
        if (bytes.length == 0 || bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("El comprobante debe pesar hasta 3 MB");
        String mime = match.group(1);
        boolean valid = switch (mime) {
            case "image/jpeg" -> bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
            case "image/png" -> bytes.length >= 8 && java.util.Arrays.equals(java.util.Arrays.copyOf(bytes, 8), new byte[]{(byte)137,80,78,71,13,10,26,10});
            case "image/webp" -> bytes.length >= 12 && new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF") && new String(bytes,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("El contenido del comprobante no coincide con su formato");
        return bytes;
    }
}
