package com.gods.saas.service.impl;
import com.gods.saas.config.WhatsappOnboardingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
@Service @RequiredArgsConstructor
public class WhatsappCredentialCipher {
    private final WhatsappOnboardingProperties properties;
    private final SecureRandom random=new SecureRandom();
    private byte[] key() { return Base64.getDecoder().decode(properties.getEncryptionKey()); }
    public boolean ready() { try { return key().length==32; } catch (RuntimeException bad) { return false; } }
    public String encrypt(Long tenant,String account,String secret) { return transform(true,tenant,account,secret); }
    public String decrypt(Long tenant,String account,String encrypted) { return transform(false,tenant,account,encrypted); }
    private String transform(boolean encrypt,Long tenant,String account,String value) {
        try {
            if (!ready()) throw new IllegalStateException();
            byte[] iv=new byte[12];byte[] input;
            if (encrypt) { random.nextBytes(iv);input=value.getBytes(StandardCharsets.UTF_8); }
            else { String[] parts=value.split("\\.",-1);if(parts.length!=3 || !parts[0].equals("v1")) throw new IllegalStateException();iv=Base64.getUrlDecoder().decode(parts[1]);input=Base64.getUrlDecoder().decode(parts[2]);if(iv.length!=12)throw new IllegalStateException(); }
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(encrypt?Cipher.ENCRYPT_MODE:Cipher.DECRYPT_MODE,new SecretKeySpec(key(),"AES"),new GCMParameterSpec(128,iv));
            cipher.updateAAD((tenant+":"+account).getBytes(StandardCharsets.UTF_8));
            byte[] output=cipher.doFinal(input);
            return encrypt ? "v1."+Base64.getUrlEncoder().withoutPadding().encodeToString(iv)+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(output)
                : new String(output,StandardCharsets.UTF_8);
        } catch (Exception bad) { throw new IllegalStateException("No se pudieron proteger las credenciales de WhatsApp."); }
    }
}
