package com.bone.lowcode.integration.uitls;

import com.bone.lowcode.integration.enums.SignTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import java.io.*;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;

/**
 * 非对称加密RAS
 */
@Slf4j
public class RsaUtils {
    private static final String PUB_KEY = "publicKey";
    private static final String PRI_KEY = "privateKey";
    private static final String CHARSET = "UTF-8";
    private static final String RSA_ALGORITHM = "RSA";
    private static final Integer KEY_LENGTH = 1024;
    public static final Map<String, String> keyMap = createKeys(KEY_LENGTH);

    /**
     * 生成秘钥对
     *
     * @param keySize 秘钥长度
     * @return Map<String,String>
     */
    private static Map<String, String> createKeys(int keySize) {
        KeyPairGenerator kpg;
        try {
            kpg = KeyPairGenerator.getInstance(RSA_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            log.error("No such algorithm-->[{}]",RSA_ALGORITHM,e);
            return null;
        }
        //初始化KeyPairGenerator对象,密钥长度
        kpg.initialize(keySize);
        //生成密匙对
        KeyPair keyPair = kpg.generateKeyPair();
        //得到公钥
        Key publicKey = keyPair.getPublic();
        String publicKeyStr = new String(Base64.encodeBase64(publicKey.getEncoded()));
        //得到私钥
        Key privateKey = keyPair.getPrivate();
        String privateKeyStr = new String(Base64.encodeBase64(privateKey.getEncoded()));
        Map<String, String> keyPairMap = new HashMap<>(1);
        keyPairMap.put(PUB_KEY, publicKeyStr);
        keyPairMap.put(PRI_KEY, privateKeyStr);
        return keyPairMap;
    }

    /**
     * 得到公钥
     *
     * @throws Exception
     */
    public static String getPublicKey()  {
        return keyMap.get(PUB_KEY);
    }

    /**
     * 得到私钥
     *
     * @throws Exception
     */
    public static String getPrivateKey()  {
        return keyMap.get(PRI_KEY);
    }

    /**
     * 公钥加密
     *
     * @param data
     * @param publicKey
     * @return
     */
    public static String encrypt(String data, String publicKey) {
        try {
            //通过X509编码的Key指令获得公钥对象
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(Base64.decodeBase64(publicKey));
            RSAPublicKey key = (RSAPublicKey) keyFactory.generatePublic(x509KeySpec);
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return new String(Base64.encodeBase64(rsaCodec(cipher, Cipher.ENCRYPT_MODE, data.getBytes(CHARSET), key.getModulus().bitLength())));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 私钥解密
     *
     * @param data
     * @param privateKey
     * @return
     */
    public static String decrypt(String data, String privateKey) {
        try {
            //通过PKCS#8编码的Key指令获得私钥对象
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(Base64.decodeBase64(privateKey));
            RSAPrivateKey key = (RSAPrivateKey) keyFactory.generatePrivate(pkcs8KeySpec);
            return decrypt(data, key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String decrypt(String data, RSAPrivateKey privateKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return new String(rsaCodec(cipher, Cipher.DECRYPT_MODE, Base64.decodeBase64(data), privateKey.getModulus().bitLength()), CHARSET);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * RSA加解密
     * @param cipher
     * @param opMode
     * @param dataBytes
     * @param keySize
     * @return
     * @throws IOException
     * @throws IOException
     */
    private static byte[] rsaCodec(Cipher cipher, int opMode, byte[] dataBytes, int keySize) throws Exception {
        int maxBlock;
        if (opMode == Cipher.DECRYPT_MODE) {
            maxBlock = keySize / 8;
        } else {
            maxBlock = keySize / 8 - 11;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int offSet = 0;
        byte[] buff;
        int i = 0;
        while (dataBytes.length > offSet) {
            if (dataBytes.length - offSet > maxBlock) {
                buff = cipher.doFinal(dataBytes, offSet, maxBlock);
            } else {
                buff = cipher.doFinal(dataBytes, offSet, dataBytes.length - offSet);
            }
            out.write(buff, 0, buff.length);
            i++;
            offSet = i * maxBlock;
        }

        byte[] resultDataBytes = out.toByteArray();
        out.close();
        return resultDataBytes;
    }


    /**
     * 加签
     * @param data
     * @param sk
     * @return
     * @throws Exception
     */
    public static String sign(String data, String sk, SignTypeEnum signTypeEnum) throws Exception {
        byte[] keyBytes = getPrivateKey(sk).getEncoded();
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey key = keyFactory.generatePrivate(keySpec);
        Signature signature = getSignature(signTypeEnum);
        signature.initSign(key);
        signature.update(data.getBytes());
        return new String(Base64.encodeBase64(signature.sign()));
    }

    public static Signature getSignature(SignTypeEnum signEnum) throws NoSuchAlgorithmException {
        return Signature.getInstance(signEnum.name());
    }

    /**
     * 验签
     * @param srcData
     * @param pk
     * @param sign
     * @return
     * @throws Exception
     */
    public static boolean verify(String srcData, String pk, String sign, SignTypeEnum signTypeEnum) throws Exception {
        byte[] keyBytes = getPublicKey(pk).getEncoded();
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey key = keyFactory.generatePublic(keySpec);
        Signature signature = getSignature(signTypeEnum);
        signature.initVerify(key);
        signature.update(srcData.getBytes());
        return signature.verify(Base64.decodeBase64(sign.getBytes()));
    }

    public static PublicKey getPublicKey(String publicKey) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        byte[] decodedKey = Base64.decodeBase64(publicKey.getBytes());
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decodedKey);
        return keyFactory.generatePublic(keySpec);
    }

    private static RSAPrivateKey getPrivateKey(String privateKey) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
        PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(Base64.decodeBase64(privateKey));
        return (RSAPrivateKey) keyFactory.generatePrivate(pkcs8KeySpec);
    }
}
