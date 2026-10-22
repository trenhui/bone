package com.bone.system.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;

/** AES-256-GCM 配置加密的往返契约测试（详设 §7.2 落地验证）。 */
class AesConfigCipherAdapterTest {

  private static final String KEY = "unit-test-key";

  /**
   * 无激活 profile 的环境（dev/test 语义：缺密钥走降级而不是拒绝启动）。
   *
   * <p>用真实 {@link StandardEnvironment} 而不是 mock——守卫走的是 {@code Environment#acceptsProfiles}，mock
   * 会把该方法本身桩成默认 false，prod 用例必然假绿。
   */
  private static Environment nonProdEnv() {
    return new StandardEnvironment();
  }

  private static Environment prodEnv() {
    StandardEnvironment env = new StandardEnvironment();
    env.setActiveProfiles("prod");
    return env;
  }

  @Test
  void encryptAndDecryptRoundTrip() {
    AesConfigCipherAdapter cipher = new AesConfigCipherAdapter(KEY, nonProdEnv());
    String cipherText = cipher.encrypt("s3cret-password");

    assertThat(cipherText).startsWith("enc:v1:").isNotEqualTo("s3cret-password");
    assertThat(cipher.decrypt(cipherText)).isEqualTo("s3cret-password");
  }

  /** 每次加密 IV 随机，同一明文产生不同密文（防重放/频率分析）。 */
  @Test
  void encryptIsNonDeterministic() {
    AesConfigCipherAdapter cipher = new AesConfigCipherAdapter(KEY, nonProdEnv());
    assertThat(cipher.encrypt("same-plain")).isNotEqualTo(cipher.encrypt("same-plain"));
  }

  /** 已加密值再加密必须原样返回（幂等），避免更新路径二次加密导致不可解。 */
  @Test
  void encryptIsIdempotentOnCipherText() {
    AesConfigCipherAdapter cipher = new AesConfigCipherAdapter(KEY, nonProdEnv());
    String once = cipher.encrypt("s3cret");
    assertThat(cipher.encrypt(once)).isEqualTo(once);
  }

  /** 无前缀的存量明文（历史种子数据）解密原样返回，不需要停机迁移。 */
  @Test
  void decryptLegacyPlaintextPassthrough() {
    AesConfigCipherAdapter cipher = new AesConfigCipherAdapter(KEY, nonProdEnv());
    assertThat(cipher.decrypt("plain-old-value")).isEqualTo("plain-old-value");
  }

  /** 密钥不匹配（换环境/轮换后）解密失败返回 null，由调用方脱敏，绝不静默吐出垃圾串。 */
  @Test
  void decryptWithWrongKeyReturnsNull() {
    String cipherText = new AesConfigCipherAdapter(KEY, nonProdEnv()).encrypt("s3cret");
    assertThat(new AesConfigCipherAdapter("another-key", nonProdEnv()).decrypt(cipherText))
        .isNull();
  }

  /** 未配置密钥时降级明文存储（与历史行为一致 + 启动告警），但不伪装成已加密。 */
  @Test
  void missingKeyStoresPlaintext() {
    AesConfigCipherAdapter cipher = new AesConfigCipherAdapter("", nonProdEnv());
    assertThat(cipher.encrypt("s3cret")).isEqualTo("s3cret");
    assertThat(cipher.decrypt("s3cret")).isEqualTo("s3cret");
    // 降级模式必须自报「加密不可用」，写路径据此 fail-closed（见 ConfigApplicationServiceTest）
    assertThat(cipher.encryptionEnabled()).isFalse();
  }

  /** 配了密钥即自报加密可用——写路径的放行条件。 */
  @Test
  void configuredKeyEnablesEncryption() {
    assertThat(new AesConfigCipherAdapter(KEY, nonProdEnv()).encryptionEnabled()).isTrue();
  }

  /**
   * prod 缺密钥必须启动即失败。
   *
   * <p>判别力：删掉构造函数里的 prod 守卫，本测试转红——那正是「生产把明文写进 encrypted 列」的入口。
   */
  @Test
  void missingKeyInProdFailsFastAtStartup() {
    assertThatThrownBy(() -> new AesConfigCipherAdapter("", prodEnv()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("BONE_CONFIG_ENCRYPT_KEY");
  }

  /** prod 配了密钥照常启动（守卫只管缺失，不阻止正常配置）。 */
  @Test
  void configuredKeyInProdStartsNormally() {
    AesConfigCipherAdapter cipher = new AesConfigCipherAdapter(KEY, prodEnv());
    assertThat(cipher.encryptionEnabled()).isTrue();
  }
}
