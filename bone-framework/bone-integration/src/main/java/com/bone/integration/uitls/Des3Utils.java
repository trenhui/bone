package com.bone.integration.uitls;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.NoSuchPaddingException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.security.InvalidAlgorithmParameterException;
import java.io.IOException;

@Slf4j
public class Des3Utils {

    private static final String DES_ALGORITHM = "DESede";  // 3DES算法
    private static final String CHARSET = "UTF-8";  // 字符集
    public static final Map<String, String> keyMap = createKeys();

    /**
     * 生成秘钥
     *
     * @return Map<String, String>
     */
    private static Map<String, String> createKeys() {
        try {
            // KeyGenerator生成3DES秘钥
            KeyGenerator keyGenerator = KeyGenerator.getInstance(DES_ALGORITHM);
            keyGenerator.init(168);  // 3DES的密钥长度通常为168位
            SecretKey secretKey = keyGenerator.generateKey();
            String secretKeyStr = Base64.getEncoder().encodeToString(secretKey.getEncoded());
            Map<String, String> keyPairMap = new HashMap<>();
            keyPairMap.put("secretKey", secretKeyStr);
            return keyPairMap;
        } catch (NoSuchAlgorithmException e) {
            log.error("Error generating key for 3DES", e);
            return null;
        }
    }

    /**
     * 获取3DES密钥
     */
    public static String getSecretKey() {
        return keyMap.get("secretKey");
    }

    /**
     * 3DES加密
     *
     * @param data
     * @param secretKey
     * @return
     */
    public static String encrypt(String data, String secretKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(secretKey);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, DES_ALGORITHM);
            Cipher cipher = Cipher.getInstance(DES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encryptedData = cipher.doFinal(data.getBytes(CHARSET));
            return Base64.getEncoder().encodeToString(encryptedData);
        } catch (Exception e) {
            throw new RuntimeException("3DES encryption failed", e);
        }
    }

    /**
     * 3DES解密
     *
     * @param data
     * @param secretKey
     * @return
     */
    public static String decrypt(String data, String secretKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(secretKey);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, DES_ALGORITHM);
            Cipher cipher = Cipher.getInstance(DES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decryptedData = cipher.doFinal(Base64.getDecoder().decode(data));
            return new String(decryptedData, CHARSET);
        } catch (Exception e) {
            throw new RuntimeException("3DES decryption failed", e);
        }
    }

    /**
     * 加签（可选：若要结合数字签名）
     * 使用3DES加密的消息生成签名
     * @param data
     * @param secretKey
     * @return
     * @throws Exception
     */
    public static String sign(String data, String secretKey) throws Exception {
        // 使用3DES的密钥加密消息来生成“签名”
        return encrypt(data, secretKey);
    }

    /**
     * 验签
     * @param data
     * @param secretKey
     * @param sign
     * @return
     * @throws Exception
     */
    public static boolean verify(String data, String secretKey, String sign) throws Exception {
        String decryptedData = decrypt(sign, secretKey);
        return decryptedData.equals(data);
    }

}
