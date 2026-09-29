package com.bone.platform.alert.common;

/**
 * bone-notification 业务错误码（登记见 {@code doc/architecture/Bone-错误码登记.md} §6）。
 *
 * <p><b>为何需要这一层</b>：此前用户面以裸 {@code AlertException(RuntimeException)} 抛出，无码/无状态/无 i18n， 全部被兜底成 HTTP
 * 500 + 中文 message，前端无法按码提示。稳定码是跨系统契约，中文说明只是 fallback。
 *
 * <p><b>用法</b>：抛出走 {@link NotificationErrors#of(String, Object)}，不要在抛出点手写 HTTP 状态—— 状态与码的配对真源在
 * {@link NotificationErrors}，两处各写一遍必然漂移。
 *
 * <p><b>命名</b>：{@code NOTIFICATION_} 为通知模块前缀，格式 {@code NOTIFICATION_{SNAKE_CASE_REASON}}。
 */
public final class NotificationErrorCodes {

  /** 站内信不存在。 */
  public static final String NOT_FOUND = "NOTIFICATION_NOT_FOUND";

  /** 跨租户访问被拒绝：站内信不属于当前调用方租户或用户。 */
  public static final String ACCESS_DENIED = "NOTIFICATION_ACCESS_DENIED";

  /** 入参非法：userId 缺失或 limit 超限。 */
  public static final String INVALID_PARAM = "NOTIFICATION_INVALID_PARAM";

  /** 缺少租户上下文，无法判定站内信归属（失败关闭）。 */
  public static final String TENANT_MISMATCH = "NOTIFICATION_TENANT_MISMATCH";

  private NotificationErrorCodes() {}
}
