package com.bone.metadata.catalog.domain.gateway;

/** 当前用户端口：返回登录用户的账号 ID（JWT {@code userId} claim）。 */
public interface CurrentUserProvider {

  /**
   * 当前登录用户 ID；**无主体场景返回 {@code null}**（E2E 安全关闭 / API-Key 服务凭证 / 定时任务等非人主体）。
   *
   * <p>G1② 裁定（2a §10.4）：无主体＝平台运维通道，应用角色校验**豁免**（服务级凭证的权限已在认证层收敛，见 SecurityConfig API-Key
   * authorities），但调用方必须记录审计主体（{@code currentOperator} 为 null 时落 WARN）。
   */
  Long currentUserIdOrNull();
}
