package com.bone.integration.infrastructure.security;

import com.bone.integration.application.port.out.ConnectorSecretCipherPort;
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
 * 连接器凭据的 AES-256-GCM 加解密适配器。
 *
 * <p><b>存储格式</b>：{@code enc:v1:<base64(iv)>:<base64(ciphertext)>}，与 {@code bone-system} 的配置加密
 * 同一套格式与前缀（版本位留给将来轮换算法）。
 *
 * <p><b>存量免迁移</b>：无 {@code enc:v1:} 前缀的值按明文处理 —— 加密原样返回、解密原样返回。 这是本设计
 * 能安全上线的关键：存量连接器的凭据无需停机脚本改写，装上即生效。
 *
 * <p><b>密钥来源</b>：{@code bone.integration.config.encrypt-key}（生产经环境变量 {@code
 * BONE_INTEGRATION_CONFIG_ENCRYPT_KEY} 注入），任意口令经 SHA-256 派生 256 位密钥。
 *
 * <p><b>未配置密钥时降级为明文并告警一次</b>（与 bone-system 一致）：宁可诚实地不加密，也不要用写死的 开发密钥伪装安全 ——
 * 后者会让「以为已加密」比「明确未加密」危险得多。真正的兜底是**读端一律不返回凭据**， 加密只负责降低库内裸奔的面。
 */
@Slf4j
@Component
public class AesConnectorSecretCipherAdapter implements ConnectorSecretCipherPort {

  private static final String PREFIX = "enc:v1:";
  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_IV_BYTES = 12;
  private static final int GCM_TAG_BITS = 128;

  private final SecretKeySpec keySpec;
  private final SecureRandom secureRandom = new SecureRandom();

  public AesConnectorSecretCipherAdapter(
      @Value("${bone.integration.config.encrypt-key:${BONE_INTEGRATION_CONFIG_ENCRYPT_KEY:}}")
          String secret) {
    if (secret == null || secret.isBlank()) {
      this.keySpec = null;
      log.warn(
          "[ConnectorSecretCipher] 未配置 BONE_INTEGRATION_CONFIG_ENCRYPT_KEY，"
              + "连接器凭据将明文存储（读端仍一律不返回凭据）。生产环境必须注入加密密钥。");
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
      throw new IllegalStateException("连接器凭据加密密钥派生失败", ex);
    }
  }

  @Override
  public String encrypt(String plaintext) {
    if (plaintext == null || keySpec == null || plaintext.startsWith(PREFIX)) {
      // 未启用加密 / 空值 / 已是密文（幂等，避免二次加密把密文再加密一层）
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
      // 不回落成明文：静默回落会把一次配置错误变成凭据裸奔，且事后无从察觉
      throw new IllegalStateException("连接器凭据加密失败", ex);
    }
  }

  @Override
  public String decrypt(String ciphertext) {
    if (ciphertext == null || !ciphertext.startsWith(PREFIX)) {
      return ciphertext;
    }
    if (keySpec == null) {
      log.error("[ConnectorSecretCipher] 存量密文无法解密：未配置加密密钥" + "（密钥丢失或换环境后密文不可恢复）");
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
      log.error("[ConnectorSecretCipher] 连接器凭据解密失败（密钥不匹配或数据损坏）: {}", ex.getMessage());
      return null;
    }
  }
}
