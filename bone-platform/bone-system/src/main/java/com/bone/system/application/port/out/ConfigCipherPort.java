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

  /**
   * 加密能力是否真正可用（密钥已配置）。
   *
   * <p><b>为什么写路径必须先问这一句</b>：未配置密钥时 {@link #encrypt} 按历史行为降级为原样返回（明文存储）， 若调用方不察觉，{@code
   * encrypted=true} 的配置就会以明文落库而界面仍显示「已加密」—— 比不加密更糟的「脱敏表演」。写路径据此 fail-closed 拒绝落库，
   * 而不是把「标了加密却没加密」的数据写进库里。
   */
  boolean encryptionEnabled();
}
