package com.bone.integration.uitls.encrypt;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.HexUtil;
import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.asymmetric.KeyType;
import cn.hutool.crypto.asymmetric.SM2;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;

@Slf4j
public class Sm2Util {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }


    /**
     * 使用 SM2 私钥解密 SM4 密钥
     *
     * @param encryptedKey  十六进制格式的加密密钥
     * @param sm2PrivateKey Base64 编码的 SM2 私钥
     * @return 解密后的 SM4 密钥（十六进制格式）
     */
    public static String decryptSm4Key(String encryptedKey, String sm2PrivateKey) {
        try {
            // 从Base64编码的PKCS8格式中解析出SM2密钥对
            KeyFactory keyFactory = KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
            PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(Base64.decode(sm2PrivateKey));
            PrivateKey privateKey = keyFactory.generatePrivate(pkcs8KeySpec);

            // 使用BCECPrivateKey获取私钥的具体数值
            BCECPrivateKey ecPrivateKey = (BCECPrivateKey) privateKey;
            String actualPrivateKey = HexUtil.encodeHexStr(ecPrivateKey.getD().toByteArray());

            return SmUtil.sm2(actualPrivateKey, null)
                    .decryptStr(encryptedKey, KeyType.PrivateKey);
        } catch (Exception e) {
            log.error("SM4密钥解密失败: {}", e.getMessage());
            throw new RuntimeException("SM4密钥解密失败: " + e.getMessage());
        }
    }

    /**
     * 使用 SM2 公钥加密 SM4 密钥
     *
     * @param sm4Key       SM4 密钥（十六进制格式）
     * @param sm2PublicKey Base64 编码的 SM2 公钥
     * @return 十六进制格式的加密 SM4 密钥
     */
    public static String encryptSm4Key(String sm4Key, String sm2PublicKey) {
        try {
            // 使用SM2加密密钥并返回十六进制字符串
            return SmUtil.sm2(null, sm2PublicKey).encryptHex(sm4Key, KeyType.PublicKey);
        } catch (Exception e) {
            log.error("SM4密钥加密失败", e);
            throw new RuntimeException("SM4密钥加密失败", e);
        }
    }


    public static void main(String[] args) {
        SM2 sm2 = SmUtil.sm2();
        System.out.println("Private Key (Base64): " + sm2.getPrivateKeyBase64());
        System.out.println("Public Key (Base64): " + sm2.getPublicKeyBase64());


    }
}
