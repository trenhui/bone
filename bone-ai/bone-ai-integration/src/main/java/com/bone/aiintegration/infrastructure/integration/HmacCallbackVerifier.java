package com.bone.aiintegration.infrastructure.integration;

import com.bone.aiintegration.domain.gateway.CallbackVerifier;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 入站回调验签（与出站 {@code WebhookSigner} 对称）。
 *
 * <p>用共享密钥对 payload 计算 HMAC，与回调携带的签名做<b>常量时间</b>比较（{@link MessageDigest#isEqual}，防时序侧信道）； 归一化
 * {@code sha256=} 前缀与大小写。验不过抛 {@link SecurityException}（fail-closed）。
 */
public class HmacCallbackVerifier implements CallbackVerifier {

  private static final String ALGO = "HmacSHA256";
  private final String secret;

  public HmacCallbackVerifier(String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalArgumentException("回调验签密钥不可为空");
    }
    this.secret = secret;
  }

  @Override
  public void verify(String payload, String signature) {
    if (payload == null) {
      throw new SecurityException("回调 payload 为空，拒绝");
    }
    if (signature == null || signature.isBlank()) {
      throw new SecurityException("缺少回调签名，拒绝");
    }
    String expected = hmacHex(payload, secret);
    String provided = normalize(signature);
    if (!MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
      throw new SecurityException("回调验签失败：签名不匹配");
    }
  }

  /** 计算 payload 的 HMAC-SHA256 十六进制串（测试据以构造合法签名）。 */
  static String hmacHex(String payload, String secret) {
    try {
      Mac mac = Mac.getInstance(ALGO);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGO));
      byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(raw);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("回调验签计算失败", e);
    }
  }

  /** 归一化签名：去 {@code sha256=} 前缀、转小写，便于与期望 hex 比较。 */
  private static String normalize(String signature) {
    String s = signature.trim();
    int idx = s.indexOf('=');
    if (idx >= 0) {
      s = s.substring(idx + 1);
    }
    return s.toLowerCase(Locale.ROOT);
  }
}
