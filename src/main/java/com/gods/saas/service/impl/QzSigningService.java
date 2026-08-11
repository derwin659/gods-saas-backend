package com.gods.saas.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Service
public class QzSigningService {
    private final String certificate;
    private final PrivateKey privateKey;

    public QzSigningService(
            @Value("${qz.signing.certificate-base64:}") String certificateBase64,
            @Value("${qz.signing.private-key-base64:}") String privateKeyBase64
    ) {
        this.certificate = decodeConfig(certificateBase64);
        this.privateKey = parsePrivateKey(decodeConfig(privateKeyBase64));
    }

    public String certificate() {
        requireConfigured();
        return certificate;
    }

    public String sign(String payload) {
        requireConfigured();
        if (!StringUtils.hasText(payload) || payload.length() > 1_000_000) {
            throw new IllegalArgumentException("Solicitud de firma QZ invalida.");
        }
        try {
            Signature signer = Signature.getInstance("SHA512withRSA");
            signer.initSign(privateKey);
            signer.update(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo firmar la solicitud QZ.", ex);
        }
    }

    private void requireConfigured() {
        if (!StringUtils.hasText(certificate) || privateKey == null) {
            throw new IllegalStateException("La firma QZ no esta configurada en el servidor.");
        }
    }

    private static String decodeConfig(String value) {
        if (!StringUtils.hasText(value)) return "";
        try {
            return new String(Base64.getDecoder().decode(value.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Configuracion QZ Base64 invalida.", ex);
        }
    }

    private static PrivateKey parsePrivateKey(String pem) {
        if (!StringUtils.hasText(pem)) return null;
        try {
            String body = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(body);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception ex) {
            throw new IllegalStateException("La clave privada QZ no es PKCS#8 RSA valida.", ex);
        }
    }
}