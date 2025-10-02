package com.bone.lowcode.integration.uitls.encrypt;

import com.bone.lowcode.integration.enums.EncodingTypeEnum;
import com.bone.lowcode.integration.uitls.EncodingUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Security;

public class SM4EncryptUtils {
    static {
        // 添加安全提供者（SM2，SM3，SM4等加密算法，CBC、CFB等加密模式，PKCS7Padding等填充方式，不在Java标准库中，由BouncyCastleProvider实现）
        Security.addProvider(new BouncyCastleProvider());
    }

    public static String encryptECB(String plainString, String privateKey, EncodingTypeEnum encodingTypeEnum) {
        String cipherString;
        try {
            // 指定加密算法
            String algorithm = "SM4";
            // 创建密钥规范
            SecretKeySpec secretKeySpec = new SecretKeySpec(privateKey.getBytes(StandardCharsets.UTF_8), algorithm);
            // 获取Cipher对象实例（BC中SM4默认使用ECB模式和PKCS5Padding填充方式，因此下列模式和填充方式无需指定）
            Cipher cipher = Cipher.getInstance(algorithm + "/ECB/PKCS5Padding");
            // 初始化Cipher为加密模式
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            // 获取加密byte数组
            byte[] cipherBytes = cipher.doFinal(plainString.getBytes(StandardCharsets.UTF_8));
            // 输出为Base64编码
            cipherString = EncodingUtils.encode(cipherBytes, encodingTypeEnum);
            return cipherString;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String decryptECB(String cipherString, String privateKey, EncodingTypeEnum encodingTypeEnum) {
        String plainString;
        try {
            // 指定加密算法
            String algorithm = "SM4";
            // 创建密钥规范
            SecretKeySpec secretKeySpec = new SecretKeySpec(privateKey.getBytes(StandardCharsets.UTF_8), algorithm);
            // 获取Cipher对象实例（BC中SM4默认使用ECB模式和PKCS5Padding填充方式，因此下列模式和填充方式无需指定）
            Cipher cipher = Cipher.getInstance(algorithm + "/ECB/PKCS5Padding");
            // 初始化Cipher为解密模式
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
            // 获取加密byte数组
            byte[] cipherBytes = cipher.doFinal(EncodingUtils.decode(cipherString, encodingTypeEnum));
            // 输出为字符串
            plainString = new String(cipherBytes);
            return plainString;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String encryptCBC(String plainString, String privateKey, String iv, EncodingTypeEnum encodingTypeEnum) {
//        String cipherString;
//        try {
//            // 指定加密算法
//            String algorithm = "SM4";
//            // 创建密钥规范
//            SecretKeySpec secretKeySpec = new SecretKeySpec(privateKey.getBytes(StandardCharsets.UTF_8), algorithm);
//            // 获取Cipher对象实例（BC中SM4默认使用ECB模式和PKCS5Padding填充方式，因此下列模式和填充方式无需指定）
//            Cipher cipher = Cipher.getInstance(algorithm + "/CBC/PKCS7Padding");
//            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
//            // 初始化Cipher为加密模式
//            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);
//            // 获取加密byte数组
//            byte[] cipherBytes = cipher.doFinal(plainString.getBytes(StandardCharsets.UTF_8));
//            // 输出为Base64编码
//            cipherString = EncodingUtils.encode(cipherBytes, encodingTypeEnum);
//            return cipherString;
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
        return Sm4Util.encryptBody(plainString, privateKey);
    }

    public static String decryptCBC(String cipherString, String privateKey, String iv, EncodingTypeEnum encodingTypeEnum) {
//        String plainString;
//        try {
//            // 指定加密算法
//            String algorithm = "SM4";
//            // 创建密钥规范
//            SecretKeySpec secretKeySpec = new SecretKeySpec(privateKey.getBytes(StandardCharsets.UTF_8), algorithm);
//            // 获取Cipher对象实例（BC中SM4默认使用ECB模式和PKCS5Padding填充方式，因此下列模式和填充方式无需指定）
//            Cipher cipher = Cipher.getInstance(algorithm + "/CBC/PKCS7Padding");
//            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
//            // 初始化Cipher为加密模式
//            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
//            // 获取加密byte数组
//            byte[] cipherBytes = cipher.doFinal(EncodingUtils.decode(cipherString, encodingTypeEnum));
//            plainString = new String(cipherBytes);
//            return plainString;
//        } catch (Exception e) {
//            throw new RuntimeException("解密失败: " + e.getMessage(), e);
//        }
        return Sm4Util.decryptBody(cipherString, privateKey);
    }
}
