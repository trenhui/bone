package com.bone.system.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** AES-256-GCM 配置加密的往返契约测试（详设 §7.2 落地验证）。 */
class AesConfigCipherAdapterTest {

  private static final String KEY = "unit-test-key";

  @Test
  void encryptAndDecryptRoundTrip() {
    AesConfigCipherAdapter cipher = new AesConfigCipherAdapter(KEY);
    String cipherText = cipher.encrypt("s3cret-password");

    assertThat(cipherText).startsWith("enc:v1:").isNotEqualTo("s3cret-password");
    assertThat(cipher.decrypt(cipherText)).isEqualTo("s3cret-password");
  }

  /** 每次加密 IV 随机，同一明文产生不同密文（防重放/频率分析）。 */
  @Test
  void encryptIsNonDeterministic() {
    AesConfigCipherAdapter cipher = new AesConfigCipherAdapter(KEY);
    assertThat(cipher.encrypt("same-plain")).isNotEqualTo(cipher.encrypt("same-plain"));
  }

  /** 已加密值再加密必须原样返回（幂等），避免更新路径二次加密导致不可解。 */
  @Test
  void encryptIsIdempotentOnCipherText() {
    AesConfigCipherAdapter cipher = new AesConfigCipherAdapter(KEY);
    String once = cipher.encrypt("s3cret");
    assertThat(cipher.encrypt(once)).isEqualTo(once);
  }

  /** 无前缀的存量明文（历史种子数据）解密原样返回，不需要停机迁移。 */
  @Test
  void decryptLegacyPlaintextPassthrough() {
    AesConfigCipherAdapter cipher = new AesConfigCipherAdapter(KEY);
    assertThat(cipher.decrypt("plain-old-value")).isEqualTo("plain-old-value");
  }

  /** 密钥不匹配（换环境/轮换后）解密失败返回 null，由调用方脱敏，绝不静默吐出垃圾串。 */
  @Test
  void decryptWithWrongKeyReturnsNull() {
    String cipherText = new AesConfigCipherAdapter(KEY).encrypt("s3cret");
    assertThat(new AesConfigCipherAdapter("another-key").decrypt(cipherText)).isNull();
  }

  /** 未配置密钥时降级明文存储（与历史行为一致 + 启动告警），但不伪装成已加密。 */
  @Test
  void missingKeyStoresPlaintext() {
    AesConfigCipherAdapter cipher = new AesConfigCipherAdapter("");
    assertThat(cipher.encrypt("s3cret")).isEqualTo("s3cret");
    assertThat(cipher.decrypt("s3cret")).isEqualTo("s3cret");
  }
}
