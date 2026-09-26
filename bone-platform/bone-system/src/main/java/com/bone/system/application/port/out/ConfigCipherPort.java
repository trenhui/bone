package com.bone.system.application.port.out;

/**
 * 配置值加解密出站端口：敏感配置（{@code encrypted=true}）在落库前加密、内部消费时解密。
 *
 * <p>为什么是端口：加密算法与密钥管理是基础设施关切（AES-256-GCM、密钥来自环境变量）， 应用层只依赖「给明文拿密文、给密文拿明文」的契约（E-10.2）。
 */
public interface ConfigCipherPort {

  /** 加密明文；已是密文格式（幂等前缀）时原样返回，避免二次加密。 */
  String encrypt(String plaintext);

  /**
   * 解密密文；无加密前缀的存量明文（历史种子数据）原样返回。
   *
   * @return 解密失败（密钥不匹配 / 数据损坏）返回 {@code null}，由调用方决定脱敏或报错
   */
  String decrypt(String ciphertext);
}
