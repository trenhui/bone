package com.bone.integration.uitls.encrypt;

import cn.hutool.core.util.HexUtil;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Security;

@Slf4j
public class Sm4Util {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }



    /**
     * 使用 SM4 密钥解密请求体内容
     *
     * @param encryptedBody 十六进制格式的加密报文
     * @param sm4Key        SM4 密钥（十六进制格式）
     * @return 解密后的明文
     */
    public static String decryptBody(String encryptedBody, String sm4Key) {
        try {
            // 1. 从 Hex 格式解码
            byte[] encrypted = HexUtil.decodeHex(encryptedBody);
            byte[] keyBytes = HexUtil.decodeHex(sm4Key);

            // 2. 初始化 SM4 解密器
            Cipher cipher = Cipher.getInstance("SM4/CBC/PKCS5Padding", BouncyCastleProvider.PROVIDER_NAME);

            // 3. 使用相同的密钥作为 IV
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "SM4");
            IvParameterSpec ivSpec = new IvParameterSpec(keyBytes);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            // 4. 解密
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("请求体解密失败: {}", e.getMessage());
            throw new RuntimeException("请求体解密失败", e);
        }
    }

    /**
     * 使用 SM4 密钥加密报文内容
     *
     * @param message 原始报文
     * @param sm4Key  SM4 密钥（十六进制格式）
     * @return 十六进制格式的加密报文
     */
    public static String encryptBody(String message, String sm4Key) {
        try {
            byte[] keyBytes = HexUtil.decodeHex(sm4Key);
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);

            // 1. 初始化 SM4 加密器
            Cipher cipher = Cipher.getInstance("SM4/CBC/PKCS5Padding", BouncyCastleProvider.PROVIDER_NAME);

            // 2. 使用密钥作为 IV
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "SM4");
            IvParameterSpec ivSpec = new IvParameterSpec(keyBytes);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

            // 3. 加密并转换为 Hex 字符串
            byte[] encrypted = cipher.doFinal(messageBytes);
            return HexUtil.encodeHexStr(encrypted);
        } catch (Exception e) {
            log.error("报文加密失败", e);
            throw new RuntimeException("报文加密失败", e);
        }
    }
}
