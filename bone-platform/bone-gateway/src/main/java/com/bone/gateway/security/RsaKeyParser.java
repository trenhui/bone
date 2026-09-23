package com.bone.gateway.security;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA 公钥 PEM 解析工具（网关侧，避免引入 servlet 化的 bone-security）。 仅用于 RS256 验签（ADR-0005 双轨）；与 {@code
 * com.bone.core.security.jwt.RsaKeyParser} 逻辑一致。
 *
 * <p>格式约束：公钥须为 X.509（{@code -----BEGIN PUBLIC KEY-----}）。
 */
public final class RsaKeyParser {

  private RsaKeyParser() {}

  public static PublicKey publicKeyFromPem(String pem) {
    try {
      String der = strip(pem);
      byte[] bytes = Base64.getDecoder().decode(der);
      return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
    } catch (Exception e) {
      throw new IllegalArgumentException("无效的 RSA 公钥 PEM: " + e.getMessage(), e);
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
