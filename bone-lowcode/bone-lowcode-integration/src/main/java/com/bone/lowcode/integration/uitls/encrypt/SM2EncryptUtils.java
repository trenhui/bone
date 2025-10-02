package com.bone.lowcode.integration.uitls.encrypt;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.gm.GMObjectIdentifiers;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;

import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.util.encoders.Hex;

@Slf4j
public class SM2EncryptUtils {

    public static final String PUBLIC_KEY = "publicKey";

    public static final String PRIVATE_KEY = "privateKey";

    public static void main(String[] args) throws Exception {
        Map<String, String> map = SM2EncryptUtils.generateSmKey();
        String pk = map.get(PUBLIC_KEY);
        String sk = map.get(PRIVATE_KEY);
        String content = encrypt("123456789", pk);
        System.out.println(content);
        content = decrypt(content, sk);
        System.out.println(content);
    }

    /**
     * 生成国密公私钥对
     */
    public static Map<String, String> generateSmKey() throws Exception {
        KeyPairGenerator keyPairGenerator;
        SecureRandom secureRandom = new SecureRandom();
        ECGenParameterSpec sm2Spec = new ECGenParameterSpec("sm2p256v1");
        keyPairGenerator = KeyPairGenerator.getInstance("EC", new BouncyCastleProvider());
        keyPairGenerator.initialize(sm2Spec);
        keyPairGenerator.initialize(sm2Spec, secureRandom);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();
        String publicKeyStr = Hex.toHexString((publicKey.getEncoded()));
        String privateKeyStr = Hex.toHexString((privateKey.getEncoded()));
        return Map.of(PUBLIC_KEY, publicKeyStr, PRIVATE_KEY, privateKeyStr);
    }

    /**
     * 将Base64转码的公钥串，转化为公钥对象
     */
    public static PublicKey createPublicKey(String publicKey) {
        PublicKey publickey = null;
        try {
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(Hex.decode((publicKey)));
            KeyFactory keyFactory = KeyFactory.getInstance("EC", new BouncyCastleProvider());
            publickey = keyFactory.generatePublic(publicKeySpec);
        } catch (Exception e) {
            log.error("将Base64转码的公钥串，转化为公钥对象异常：{}", e.getMessage(), e);
        }
        return publickey;
    }

    /**
     * 将Base64转码的私钥串，转化为私钥对象
     */
    public static PrivateKey createPrivateKey(String privateKey) {
        PrivateKey publickey = null;
        try {
            PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(Hex.decode(privateKey));
            KeyFactory keyFactory = KeyFactory.getInstance("EC", new BouncyCastleProvider());
            publickey = keyFactory.generatePrivate(pkcs8EncodedKeySpec);
        } catch (Exception e) {
            log.error("将Base64转码的私钥串，转化为私钥对象异常：{}", e.getMessage(), e);
        }
        return publickey;
    }

    public static String encrypt(String data, String publicKey) {
        byte[] content = encrypt(data.getBytes(), createPublicKey(publicKey));
        return Hex.toHexString(content);
    }

    public static byte[] encrypt(byte[] data, PublicKey publicKey) {
        ECPublicKeyParameters localECPublicKeyParameters = getEcPublicKeyParameters(publicKey);
        SM2Engine localSM2Engine = new SM2Engine();
        localSM2Engine.init(true, new ParametersWithRandom(localECPublicKeyParameters, new SecureRandom()));
        byte[] arrayOfByte2;
        try {
            arrayOfByte2 = localSM2Engine.processBlock(data, 0, data.length);
            return arrayOfByte2;
        } catch (InvalidCipherTextException e) {
            log.error("SM2加密失败:{}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private static ECPublicKeyParameters getEcPublicKeyParameters(PublicKey publicKey) {
        ECPublicKeyParameters localECPublicKeyParameters = null;
        if (publicKey instanceof BCECPublicKey localECPublicKey) {
            ECParameterSpec localECParameterSpec = localECPublicKey.getParameters();
            ECDomainParameters localECDomainParameters = new ECDomainParameters(localECParameterSpec.getCurve(),
                    localECParameterSpec.getG(), localECParameterSpec.getN());
            localECPublicKeyParameters = new ECPublicKeyParameters(localECPublicKey.getQ(), localECDomainParameters);
        }
        return localECPublicKeyParameters;
    }

    public static String decrypt(String encodeData, String privateKey) {
        return new String(decrypt(Hex.decode(encodeData),  createPrivateKey(privateKey)));
    }
    /**
     * 根据privateKey对加密数据encode data，使用SM2解密
     */
    public static byte[] decrypt(byte[] encodeData, PrivateKey privateKey) {
        SM2Engine localSM2Engine = new SM2Engine();
        BCECPrivateKey sm2PriK = (BCECPrivateKey) privateKey;
        ECParameterSpec localECParameterSpec = sm2PriK.getParameters();
        ECDomainParameters localECDomainParameters = new ECDomainParameters(localECParameterSpec.getCurve(),
                localECParameterSpec.getG(), localECParameterSpec.getN());
        ECPrivateKeyParameters localECPrivateKeyParameters = new ECPrivateKeyParameters(sm2PriK.getD(),
                localECDomainParameters);
        localSM2Engine.init(false, localECPrivateKeyParameters);
        try {
            return localSM2Engine.processBlock(encodeData, 0, encodeData.length);
        } catch (InvalidCipherTextException e) {
            log.error("SM2解密失败:{}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 私钥签名
     */
    public static byte[] signByPrivateKey(byte[] data, PrivateKey privateKey) throws Exception {
        Signature sig = Signature.getInstance(GMObjectIdentifiers.sm2sign_with_sm3.toString(), BouncyCastleProvider.PROVIDER_NAME);
        sig.initSign(privateKey);
        sig.update(data);
        return sig.sign();
    }

    /**
     * 公钥验签
     */
    public static boolean verifyByPublicKey(byte[] data, PublicKey publicKey, byte[] signature) throws Exception {
        Signature sig = Signature.getInstance(GMObjectIdentifiers.sm2sign_with_sm3.toString(), BouncyCastleProvider.PROVIDER_NAME);
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature);
    }
}
