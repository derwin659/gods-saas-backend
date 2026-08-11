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
    private final String privateKeyPem;

    public QzSigningService(
            @Value("${qz.signing.certificate-base64:}") String certificateBase64,
            @Value("${qz.signing.private-key-base64:}") String privateKeyBase64
    ) {
        // La impresion es opcional: una variable invalida nunca debe impedir
        // que arranquen caja, reportes o el resto de la aplicacion.
        this.certificate = decodeConfigSafely(certificateBase64);
        this.privateKeyPem = decodeConfigSafely(privateKeyBase64);
    }

    public String certificate() {
        requireConfigured();
        if (!certificate.contains("-----BEGIN CERTIFICATE-----")) {
            throw new IllegalStateException("QZ_SIGNING_CERTIFICATE_BASE64 no contiene un certificado valido.");
        }
        return certificate;
    }

    public String sign(String payload) {
        requireConfigured();
        if (!StringUtils.hasText(payload) || payload.length() > 1_000_000) {
            throw new IllegalArgumentException("Solicitud de firma QZ invalida.");
        }
        try {
            Signature signer = Signature.getInstance("SHA512withRSA");
            signer.initSign(parsePrivateKey(privateKeyPem));
            signer.update(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo firmar la solicitud QZ.", ex);
        }
    }

    private void requireConfigured() {
        if (!StringUtils.hasText(certificate) || !StringUtils.hasText(privateKeyPem)) {
            throw new IllegalStateException("La firma QZ no esta configurada en el servidor.");
        }
    }

    private static String decodeConfigSafely(String value) {
        if (!StringUtils.hasText(value)) return "";
        try {
            return new String(Base64.getDecoder().decode(value.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            // Mantener el backend disponible; el endpoint QZ informara el error.
            return "";
        }
    }

    private static PrivateKey parsePrivateKey(String pem) {
        if (!pem.contains("-----BEGIN PRIVATE KEY-----")) {
            if (pem.contains("-----BEGIN CERTIFICATE-----")) {
                throw new IllegalStateException("Las variables QZ estan intercambiadas: la clave privada contiene el certificado.");
            }
            throw new IllegalStateException("QZ_SIGNING_PRIVATE_KEY_BASE64 no contiene una clave PKCS#8 valida.");
        }
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