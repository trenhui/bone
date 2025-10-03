package com.bone.integration.uitls;

import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.util.*;
import java.util.Base64;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.jcajce.provider.symmetric.AES;

public class EncryptionUtils {
    private static Log logger = LogFactory.getLog(AES.class);

    /* --------------------------RSA加密-----------------------------*/
    public static String encryptWithRSA(String content, String publicKey, String encryptCode) throws Exception {
        String result;
        if (encryptCode == null || encryptCode.isEmpty()) {
            result = encryptWholeContentWithRSA(content, publicKey);
        } else {
            result = encryptSpecificFields(content, publicKey, encryptCode);
        }
        System.out.println("RSA加密后的内容: " + result);
        return result;
    }

    // 加密整个内容
    private static String encryptWholeContentWithRSA(String content, String key) {
        return RsaUtils.encrypt(content, key);
    }

    // 加密指定字段（支持多层嵌套 JSON）
    public static String encryptSpecificFields(String content, String publicKey, String encryptCode) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.readValue(content, Map.class);

        // 对每个指定的字段进行加密
        for (String field : encryptCode.split(",")) {
            encryptField(map, field.split("\\."), publicKey);
        }

        return mapper.writeValueAsString(map);
    }

    // 通过路径加密指定字段
    private static void encryptField(Map<String, Object> map, String[] fieldPath, String publicKey) throws Exception {
        if (fieldPath == null || fieldPath.length == 0) {
            return;
        }

        String field = fieldPath[0]; // 获取路径的第一个字段
        Object value = map.get(field);

        if (fieldPath.length == 1) {
            // 如果是最后一个字段，进行加密
            if (value != null) {
                String encryptedValue = encryptWholeContentWithRSA(value.toString(), publicKey);
                map.put(field, encryptedValue);
            }
        } else {
            // 如果字段路径还没有到达终点，递归深入嵌套对象或数组
            if (value instanceof Map) {
                encryptField((Map<String, Object>) value, getSubPath(fieldPath), publicKey);
            } else if (value instanceof List) {
                // 处理 JSON 数组，递归加密数组中的每个对象
                for (Object item : (List<?>) value) {
                    if (item instanceof Map) {
                        encryptField((Map<String, Object>) item, getSubPath(fieldPath), publicKey);
                    }
                }
            }
        }
    }


    /* --------------------------RSA解密-----------------------------*/
        public static String decryptWithRSA(String content, String privateKey, String decryptCode) throws Exception {
            // 如果 decryptCode 为空，则解密整个内容
            if (decryptCode == null || decryptCode.isEmpty()) {
                // 解密整个内容
                return decryptWholeContentWithRSA(content, privateKey);
            } else {
                // 只解密指定字段
                return decryptSpecificFields(content, privateKey, decryptCode);
            }
        }

    // 解密整个内容
    private static String decryptWholeContentWithRSA(String content, String privateKey) throws Exception {
        return RsaUtils.decrypt(content, privateKey);
    }

    // 解密指定字段（支持多层嵌套 JSON）
    private static String decryptSpecificFields(String content, String privateKey, String decryptCode) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.readValue(content, Map.class);

        // 对每个指定的字段进行解密
        for (String field : decryptCode.split(",")) {
            decryptField(map, field.split("\\."), privateKey);
        }

        // 返回解密后的 JSON 字符串
        return mapper.writeValueAsString(map);
    }

    // 递归解密指定字段
    private static void decryptField(Map<String, Object> map, String[] fieldPath, String privateKey) throws Exception {
        if (fieldPath == null || fieldPath.length == 0) {
            return;
        }

        String field = fieldPath[0]; // 获取路径的第一个字段
        Object value = map.get(field);

        if (fieldPath.length == 1) {
            // 如果是最后一个字段，进行解密
            if (value != null) {
                String decryptedValue = decryptWholeContentWithRSA(value.toString(), privateKey);
                map.put(field, convertToObject(decryptedValue));
                System.out.println("RSA解密字段: " + field + "，解密后的值: " + decryptedValue);
            }
        } else {
            // 如果字段路径还没有到达终点，递归深入嵌套对象或数组
            if (value instanceof Map) {
                decryptField((Map<String, Object>) value, getSubPath(fieldPath), privateKey);
            } else if (value instanceof List) {
                // 处理 JSON 数组，递归解密数组中的每个对象
                for (Object item : (List<?>) value) {
                    if (item instanceof Map) {
                        decryptField((Map<String, Object>) item, getSubPath(fieldPath), privateKey);
                    }
                }
            }
        }
    }

    /**
     * 将字符串转成模型供后续处理，如freemarker转换
     * @param value
     * @return
     */
    private static Object convertToObject(String value) {
        if (value == null) {
            return null;
        }

        try {
            value = value.trim();
            ObjectMapper mapper = new ObjectMapper();
            if (value.startsWith("{") && value.endsWith("}")) {
                return mapper.readValue(value, Map.class);
            } else if (value.startsWith("[") && value.endsWith("]")) {
                return mapper.readValue(value, List.class);
            } else if (value.startsWith("<") && value.endsWith(">")) {
                XmlMapper xmlMapper = XmlMapper.xmlBuilder().build();
                return xmlMapper.readValue(value, Map.class);
            } else {
                return value;
            }
        } catch (Exception e) {
            throw new RuntimeException("解密结果转化成对象异常, content: " + value, e);
        }
    }

    /* --------------------------3DES 加密-----------------------------*/
    public static String encryptWith3DES(String content, String secretKey, String encryptCode) throws Exception {
        System.out.println("3DES原始内容: " + content);

        // 使用3DES密钥
        SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(secretKey), "DESede");

        String result;
        if (encryptCode == null || encryptCode.isEmpty()) {
            result = encryptWholeContentWith3DES(content, key);
        } else {
            result = encryptSpecificFields(content, key, encryptCode);
        }
        System.out.println("3DES加密后的内容: " + result);
        return result;
    }

    // 加密整个内容
    private static String encryptWholeContentWith3DES(String content, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encryptedBytes = cipher.doFinal(content.getBytes());
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    // 加密指定字段（支持多层嵌套 JSON）
    private static String encryptSpecificFields(String content, SecretKey key, String encryptCode) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.readValue(content, Map.class);

        // 对每个指定字段进行加密
        for (String field : encryptCode.split(",")) {
            // 使用 split("\\.") 来处理多层嵌套的字段路径
            encryptField(map, field.split("\\."), key);
        }

        // 返回加密后的 JSON 字符串
        return mapper.writeValueAsString(map);
    }

    // 递归加密指定字段
    private static void encryptField(Map<String, Object> map, String[] fieldPath, SecretKey key) throws Exception {
        if (fieldPath == null || fieldPath.length == 0) {
            return;
        }

        String field = fieldPath[0]; // 获取路径的第一个字段
        Object value = map.get(field);

        // 检查是否是最后一个字段
        if (fieldPath.length == 1) {
            if (value != null) {
                // 只有最后一个字段才进行加密
                String encryptedValue = encryptWholeContentWith3DES(value.toString(), key);
                map.put(field, encryptedValue);
                System.out.println("3DES加密字段: " + field + "，加密后的值: " + encryptedValue);
            }
        } else {
            // 如果字段路径还没有到达终点，递归深入嵌套对象或数组
            if (value instanceof Map) {
                // 递归处理嵌套的 Map 类型
                encryptField((Map<String, Object>) value, getSubPath(fieldPath), key);
            } else if (value instanceof List) {
                // 递归处理 JSON 数组，数组中每个对象都要进行加密
                for (Object item : (List<?>) value) {
                    if (item instanceof Map) {
                        encryptField((Map<String, Object>) item, getSubPath(fieldPath), key);
                    }
                }
            }
        }
    }



    /* --------------------------3DES 解密-----------------------------*/
    public static String decryptWith3DES(String content, String secretKey, String decryptCode) throws Exception {
        // 使用3DES密钥
        SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(secretKey), "DESede");

        // 如果 decryptCode 为空，则解密整个内容
        if (decryptCode == null || decryptCode.isEmpty()) {
            return decryptWholeContentWith3DES(content, key);
        } else {
            // 只解密指定字段
            return decryptSpecificFields(content, key, decryptCode);
        }
    }

    // 解密整个内容
    public static String decryptWholeContentWith3DES(String content, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key);

        byte[] encryptedBytes = Base64.getDecoder().decode(content);  // 解码 Base64 密文

        // 解密整个内容
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes);  // 返回解密后的字符串
    }

    // 解密指定字段（支持多层嵌套 JSON）
    private static String decryptSpecificFields(String content, SecretKey key, String decryptCode) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.readValue(content, Map.class);

        // 对每个指定字段进行解密
        for (String field : decryptCode.split(",")) {
            decryptField(map, field.split("\\."), key);  // 使用分割符处理多层字段
        }

        // 返回解密后的 JSON 字符串
        return mapper.writeValueAsString(map);
    }

    // 递归解密指定字段
    private static void decryptField(Map<String, Object> map, String[] fieldPath, SecretKey key) throws Exception {
        if (fieldPath == null || fieldPath.length == 0) {
            return;
        }

        String field = fieldPath[0];  // 获取路径的第一个字段
        Object value = map.get(field);

        if (fieldPath.length == 1) {
            // 如果是最后一个字段，进行解密
            if (value != null) {
                String decryptedValue = decryptWholeContentWith3DES(value.toString(), key);
                map.put(field, decryptedValue);  // 将解密后的值赋回
                System.out.println("3DES解密字段: " + field + "，解密后的值: " + decryptedValue);
            }
        } else {
            // 如果字段路径还没有到达终点，递归深入嵌套对象或数组
            if (value instanceof Map) {
                decryptField((Map<String, Object>) value, getSubPath(fieldPath), key);
            } else if (value instanceof List) {
                // 递归处理 JSON 数组，数组中每个对象都要进行解密
                for (Object item : (List<?>) value) {
                    if (item instanceof Map) {
                        decryptField((Map<String, Object>) item, getSubPath(fieldPath), key);
                    }
                }
            }
        }
    }


    public static PrivateKey generatePrivateKey(String privateKey) throws Exception {
//        System.out.println("生成私钥: " + privateKey); // 输出私钥
        byte[] keyBytes = Base64.getDecoder().decode(privateKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        return keyFactory.generatePrivate(spec);
    }

    public static PublicKey generatePublicKey(String publicKey) throws Exception {
//        System.out.println("生成公钥: " + publicKey); // 输出公钥
        byte[] keyBytes = Base64.getDecoder().decode(publicKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return keyFactory.generatePublic(spec);
    }

    // 获取字段路径中的子路径
    private static String[] getSubPath(String[] fieldPath) {
        String[] subPath = new String[fieldPath.length - 1];
        System.arraycopy(fieldPath, 1, subPath, 0, fieldPath.length - 1);
        return subPath;
    }


    /* --------------------------AES加密-----------------------------*/
    public static String encryptWithAES(String content, String secret) throws Exception {
        String encryptKey =  secret;
        String encryptString =content;
        if (encryptKey == null) {
            logger.info("encryptKey为空null");

            return null;
        }
        // 判断Key是否为16位
        logger.info(encryptKey.length());

        if (encryptKey.length() != 16) {
            logger.info("Key长度不是16位");
            return null;
        }
        byte[] raw = encryptKey.getBytes("utf-8");
        SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");//"算法/模式/补码方式"
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
        byte[] encrypted = cipher.doFinal(encryptString.getBytes("utf-8"));
        return new org.apache.commons.codec.binary.Base64().encodeToString(encrypted);

    }
    /* --------------------------AES解密-----------------------------*/
    public static String decryptWithAES(String content, String secret)  {

        String decryptKey = secret;
        String decryptString = content;
        try {
            // 判断Key是否正确
            if (decryptKey == null) {
                logger.info("Key为空null");
                return null;
            }
            // 判断Key是否为16位
            if (decryptKey.length() != 16) {
                logger.error("Key长度不是16位");
                return null;
            }
            byte[] raw = decryptKey.getBytes("utf-8");
            SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, skeySpec);
            byte[] encrypted1 = new org.apache.commons.codec.binary.Base64().decode(decryptString);//先用base64解密

            byte[] original = cipher.doFinal(encrypted1);
            String originalString = new String(original, "utf-8");
            return originalString;
        } catch (Exception ex) {
            logger.error(ex.toString());
            return null;
        }
    }

}
