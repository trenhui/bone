package com.bone.core.security.jwt;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA 密钥 PEM 解析工具。仅供 {@link JwtConfig} 启动期校验与 {@link JwtTokenService} 构造期使用。
 *
 * <p><b>格式约束</b>：公钥须为 X.509（{@code -----BEGIN PUBLIC KEY-----}）；私钥须为 PKCS#8（{@code -----BEGIN
 * PRIVATE KEY-----}）。PKCS#1（{@code BEGIN RSA PRIVATE KEY}）不被支持， 可用 {@code openssl pkcs8 -topk8
 * -nocrypt -in rsa.pem -out rsa.pkcs8.pem} 转换。
 */
public final class RsaKeyParser {

  private RsaKeyParser() {}

  public static PublicKey publicKeyFromPem(String pem) {
    try {
      byte[] der = Base64.getDecoder().decode(strip(pem));
      return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    } catch (Exception e) {
      throw new IllegalArgumentException("无效的 RSA 公钥 PEM: " + e.getMessage(), e);
    }
  }

  public static PrivateKey privateKeyFromPem(String pem) {
    try {
      byte[] der = Base64.getDecoder().decode(strip(pem));
      return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (Exception e) {
      throw new IllegalArgumentException("无效的 RSA 私钥 PEM: " + e.getMessage(), e);
    }
  }

  private static String strip(String pem) {
    if (pem == null) {
      return "";
    }
    return pem.replaceAll("-----BEGIN[^-]+-----", "")
        .replaceAll("-----END[^-]+-----", "")
        .replaceAll("\\s+", "");
  }
}
