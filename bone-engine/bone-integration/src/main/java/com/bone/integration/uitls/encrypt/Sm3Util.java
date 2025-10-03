package com.bone.integration.uitls.encrypt;

import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.digest.DigestUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;

public class Sm3Util {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * 计算签名（先MD5再SM3）
     *
     * @param content 待签名内容
     * @return 签名值（十六进制格式）
     */
    public static String calculateSignature(String content) {
        String md5Hex = DigestUtil.md5Hex(content);
        return SmUtil.sm3(md5Hex);
    }

    public static String calculateSm3(String content) {
        return SmUtil.sm3(content);
    }
}
