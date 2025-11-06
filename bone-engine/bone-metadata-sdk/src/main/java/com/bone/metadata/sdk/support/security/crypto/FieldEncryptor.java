package com.bone.metadata.sdk.support.security.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class FieldEncryptor {
    private static final String ALGORITHM = "SM4/ECB/PKCS5Padding";
    private final SecretKeySpec keySpec;

    public FieldEncryptor(String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        this.keySpec = new SecretKeySpec(keyBytes, "SM4");
    }

    public FieldEncryptor(String base64Key,String algorithm) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        this.keySpec = new SecretKeySpec(keyBytes, algorithm);
    }

    public String encrypt(String value) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (java.security.GeneralSecurityException e) {
            throw new SecurityException("Encryption failed", e);
        }
    }

    public String decrypt(String encryptedValue) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decoded = Base64.getDecoder().decode(encryptedValue);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.security.GeneralSecurityException e) {
            throw new SecurityException("Decryption failed", e);
        }
    }
}