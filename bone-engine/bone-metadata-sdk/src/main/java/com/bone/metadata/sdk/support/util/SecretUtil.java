package com.bone.metadata.sdk.support.util;

import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class SecretUtil {
  private static final String ALGORITHM = "AES";
  private static final String CIPHER_ALGORITHM = "AES/ECB/PKCS5Padding";

  public static String encrypt(String data, String key) throws Exception {
    Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
    SecretKeySpec secretKeySpec = new SecretKeySpec(Base64.getDecoder().decode(key), ALGORITHM);
    cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
    byte[] encryptedBytes = cipher.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(encryptedBytes);
  }

  public static String decrypt(String data, String key) throws Exception {
    Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
    SecretKeySpec secretKeySpec = new SecretKeySpec(Base64.getDecoder().decode(key), ALGORITHM);
    cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
    byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(data));
    return new String(decryptedBytes, java.nio.charset.StandardCharsets.UTF_8);
  }

  // 生成密钥
  public static String generateAESKey() throws Exception {
    KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
    keyGenerator.init(128); // 192 and 256 bits may not be available
    SecretKey secretKey = keyGenerator.generateKey();
    return Base64.getEncoder().encodeToString(secretKey.getEncoded());
  }
}
