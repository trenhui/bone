package com.bone.integration.uitls.encrypt;

import cn.hutool.core.util.HexUtil;
import com.bone.integration.enums.EncodingTypeEnum;
import com.bone.integration.enums.SM4EncryptTypeEnum;

import java.security.SecureRandom;

/**
 * 英大加密解密工具
 */
public class YingDaEncryptUtils {

    private static final String ALPHA_NUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String SPLIT = ";";

    /**
     * 对内容进行SM4加密
     * @param content
     * @param privateKey
     * @return
     */
    public static String sm4Encrypt(String content, String privateKey, SM4EncryptTypeEnum encryptTypeEnum, EncodingTypeEnum encodingTypeEnum) {
        return switch (encryptTypeEnum) {
            case ECB -> SM4EncryptUtils.encryptECB(content, privateKey, encodingTypeEnum);
            case CBC -> SM4EncryptUtils.encryptCBC(content, privateKey, privateKey, encodingTypeEnum);
        };
    }

    public static String sm4Decrypt(String content, String privateKey, SM4EncryptTypeEnum encryptTypeEnum, EncodingTypeEnum encodingTypeEnum) {
        return switch (encryptTypeEnum) {
            case ECB -> SM4EncryptUtils.decryptECB(content, privateKey, encodingTypeEnum);
            case CBC -> SM4EncryptUtils.decryptCBC(content, privateKey, privateKey, encodingTypeEnum);
        };
    }

    /**
     * 对SM4密钥进行加密
     * @param sm4PrivateKey
     * @param sm2PublicKey
     * @return
     */
    public static String sm2Encrypt(String sm4PrivateKey, String sm2PublicKey) {
        // return SM2EncryptUtils.encrypt(sm4PrivateKey, sm2PublicKey);
        return Sm2Util.encryptSm4Key(sm4PrivateKey, sm2PublicKey);
    }


    public static String calculateSignature(String content) {
        return Sm3Util.calculateSignature(content);
    }


    /**
     * 对SM4密钥进行解密
     * @param sm4PrivateKey
     * @param sm2PrivateKey
     * @return
     */
    public static String sm2Decrypt(String sm4PrivateKey, String sm2PrivateKey) {
//        return SM2EncryptUtils.decrypt(sm4PrivateKey, sm2PrivateKey);
        return Sm2Util.decryptSm4Key(sm4PrivateKey, sm2PrivateKey);
    }

    public static String sm3Encrypt(String content) {
/*        String stringToSign = ak + SPLIT + requestTime + SPLIT + nonce + SPLIT + (validTime == null ? "" : validTime) + SPLIT + safeMode +
                encryptKey + SPLIT + content+ SPLIT + sk;
        return SM3EncryptUtils.calculateSignature(stringToSign);*/

        return Sm3Util.calculateSm3(content);
    }

    /**
     * 生成SM4密钥
     * @return
     */
    public static String createSM4PrivateKey() {
        byte[] sm4Key = new byte[16];
        new SecureRandom().nextBytes(sm4Key);
        return HexUtil.encodeHexStr(sm4Key);
    }

    // 生成16字节的随机字节数组
    private static String generateRandomString(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            int index = random.nextInt(ALPHA_NUMERIC.length());  // 随机选择字符
            sb.append(ALPHA_NUMERIC.charAt(index));  // 追加字符
        }

        return sb.toString();
    }


    public static void main(String[] args) {
        String pk = "3059301306072a8648ce3d020106082a811ccf5501822d03420004315f55467a9a55f1b3bea4ca6837dbc91b89fad2dddb7c6c04dc86c616e082c7f8664b62b5efd5bd901ad1c51096949458c067f48434a1e8f5967a2692e7f058";
        String sk = "308193020100301306072a8648ce3d020106082a811ccf5501822d047930770201010420dc446aab48f3f363e5e8635942bf74e8fcd93ad8270228f8242f91b578f18984a00a06082a811ccf5501822da14403420004315f55467a9a55f1b3bea4ca6837dbc91b89fad2dddb7c6c04dc86c616e082c7f8664b62b5efd5bd901ad1c51096949458c067f48434a1e8f5967a2692e7f058";

        String content = "哈哈";
        String sm4PrivateKey = createSM4PrivateKey();
        System.out.println("原始密钥: " + sm4PrivateKey);

        System.out.println("原始内容: " + content);
        content = sm4Encrypt(content, sm4PrivateKey, SM4EncryptTypeEnum.CBC, EncodingTypeEnum.HEX);
        System.out.println("加密之后的内容: " + content);

        // 加密之后的密钥
        String key = sm2Encrypt(sm4PrivateKey, pk);
        System.out.println("加密之后的密钥: " + key);

        key = sm2Decrypt(key, sk);
        System.out.println("解密之后的密钥: " + key);

        content = sm4Decrypt(content, key, SM4EncryptTypeEnum.CBC, EncodingTypeEnum.HEX);
        System.out.println("解密之后的内容: " + content);
    }
}
