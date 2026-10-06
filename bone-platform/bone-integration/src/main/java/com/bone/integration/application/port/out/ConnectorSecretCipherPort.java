package com.bone.integration.application.port.out;

/**
 * 连接器敏感配置（第三方凭据）的加解密端口。
 *
 * <p><b>为什么需要它</b>：连接器的 {@code config} 是自由 JSON，第三方凭据（{@code secretKey} / {@code password} / {@code
 * token} …）原本与 {@code endpoint}、{@code bucket} 等普通参数混在同一份 明文 JSON
 * 里落库、并在详情/列表接口中原样回吐。于是**任何能读连接器的账号都能批量导出全平台第三方 凭据**—— 读端点的授权面一破（哪怕只是漏一个
 * {@code @PreAuthorize}），泄露即刻发生。
 *
 * <p><b>实现约定</b>：实现方必须做到「无前缀的存量明文原样返回」，否则上线瞬间所有存量连接器 全部失效；加密失败必须抛异常而不是回落成明文（静默回落等于把一次配置错误变成一次凭据裸奔）。
 */
public interface ConnectorSecretCipherPort {

  /**
   * 加密单个配置值。
   *
   * @param plaintext 明文；为空或已是密文时原样返回（幂等）
   * @return 密文（带版本前缀）或未启用加密时的原文
   */
  String encrypt(String plaintext);

  /**
   * 解密单个配置值。
   *
   * @param ciphertext 密文或存量明文
   * @return 明文；未启用加密时原样返回；无法解密时返回 {@code null}
   */
  String decrypt(String ciphertext);
}
