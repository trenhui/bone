package com.bone.system.infrastructure.security;

import com.bone.system.application.port.out.ConfigCipherPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM 配置值加解密适配器（详设 §7.2「敏感配置加密存储」的真实现）。
 *
 * <p><b>存储格式</b>：{@code enc:v1:&lt;base64(iv)&gt;:&lt;base64(ciphertext)&gt;}——带版本前缀便于将来轮换算法；
 * 无前缀的存量明文（历史种子数据）解密时原样返回，写入时按需补加密，无需停机迁移。
 *
 * <p><b>密钥来源</b>：{@code bone.system.config.encrypt-key}（生产经环境变量 {@code BONE_CONFIG_ENCRYPT_KEY}
 * 注入）， 任意口令经 SHA-256 派生 256 位密钥。<b>未配置密钥时加密降级为明文存储</b>（与历史行为一致）并告警一次—— 宁可诚实地不加密，也不要用写死的开发密钥伪装安全。
 */
@Slf4j
@Component
public class AesConfigCipherAdapter implements ConfigCipherPort {

  private static final String PREFIX = "enc:v1:";
  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_IV_BYTES = 12;
  private static final int GCM_TAG_BITS = 128;

  private final SecretKeySpec keySpec;
  private final SecureRandom secureRandom = new SecureRandom();

  public AesConfigCipherAdapter(
      @Value("${bone.system.config.encrypt-key:${BONE_CONFIG_ENCRYPT_KEY:}}") String secret) {
    if (secret == null || secret.isBlank()) {
      this.keySpec = null;
      log.warn(
          "[ConfigCipher] 未配置 BONE_CONFIG_ENCRYPT_KEY，敏感配置将明文存储（仅 API 响应脱敏）。" + "生产环境必须注入加密密钥。");
    } else {
      this.keySpec = deriveKey(secret);
    }
  }

  private static SecretKeySpec deriveKey(String secret) {
    try {
      byte[] keyBytes =
          MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
      return new SecretKeySpec(keyBytes, "AES");
    } catch (Exception ex) {
      throw new IllegalStateException("配置加密密钥派生失败", ex);
    }
  }

  @Override
  public String encrypt(String plaintext) {
    if (plaintext == null || keySpec == null || plaintext.startsWith(PREFIX)) {
      // 未启用加密 / 空值 / 已是密文（幂等），直接返回
      return plaintext;
    }
    try {
      byte[] iv = new byte[GCM_IV_BYTES];
      secureRandom.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      Base64.Encoder encoder = Base64.getEncoder();
      return PREFIX + encoder.encodeToString(iv) + ":" + encoder.encodeToString(cipherText);
    } catch (Exception ex) {
      throw new IllegalStateException("配置值加密失败", ex);
    }
  }

  @Override
  public String decrypt(String ciphertext) {
    if (ciphertext == null || !ciphertext.startsWith(PREFIX)) {
      return ciphertext;
    }
    if (keySpec == null) {
      log.error("[ConfigCipher] 存量密文无法解密：未配置加密密钥（密钥丢失或换环境后密文不可恢复）");
      return null;
    }
    try {
      String[] parts = ciphertext.substring(PREFIX.length()).split(":", 2);
      Base64.Decoder decoder = Base64.getDecoder();
      byte[] iv = decoder.decode(parts[0]);
      byte[] cipherText = decoder.decode(parts[1]);
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
      return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    } catch (Exception ex) {
      log.error("[ConfigCipher] 配置值解密失败（密钥不匹配或数据损坏）: {}", ex.getMessage());
      return null;
    }
  }
}
