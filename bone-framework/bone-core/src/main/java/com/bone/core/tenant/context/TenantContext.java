package com.bone.core.tenant.context;

import com.bone.core.threadlocal.TransmittableThreadLocal;

/**
 * 租户上下文（全项目唯一标准实现）。
 *
 * <p>统一使用 {@code String} 类型的租户标识，兼容雪花 ID（数值字符串）与租户编码（如 "system"、"acme"）。 底层采用 {@link
 * TransmittableThreadLocal}，支持父子线程及线程池任务间的上下文传递。
 *
 * <p>对于仍以 {@code Long} 表达租户 ID 的遗留调用方（如领域聚合根 {@code TenantAggregateRoot<Long>}）， 可使用 {@link
 * #getTenantIdAsLong()} / {@link #setTenantId(Long)} 在边界处进行转换，避免侵入式修改领域模型。 未设置时 {@link
 * #getTenantId()} 返回 {@code null}（不默认 "system"）。
 *
 * @author renhui.trh
 */
public class TenantContext {

  /** 租户ID上下文（支持父子线程及线程池任务之间的数据传递） */
  private static final TransmittableThreadLocal<String> TENANT_ID_CONTEXT =
      new TransmittableThreadLocal<>();

  /**
   * 设置当前线程的租户ID（会传递到子线程及线程池任务）。
   *
   * @param tenantId 租户ID（雪花 ID 字符串或租户编码），可为 {@code null} 表示清除
   */
  public static void setTenantId(String tenantId) {
    TENANT_ID_CONTEXT.set(tenantId);
  }

  /**
   * 以 {@code Long} 形式设置当前线程的租户ID。便于遗留调用方（如 {@code TenantAggregateRoot<Long>}）在边界处直接传入。
   *
   * @param tenantId 租户ID，可为 {@code null}
   */
  public static void setTenantId(Long tenantId) {
    TENANT_ID_CONTEXT.set(tenantId != null ? tenantId.toString() : null);
  }

  /**
   * 获取当前线程的租户ID。
   *
   * @return 租户ID字符串，未设置时返回 {@code null}
   */
  public static String getTenantId() {
    return TENANT_ID_CONTEXT.get();
  }

  /**
   * 以 {@code Long} 形式获取当前线程的租户ID，便于与使用 {@code Long} 的领域模型（如 {@code Account.getTenantId()}）
   * 对齐比较。非数值型租户编码会被解析为 {@code null}。
   *
   * @return 租户ID的 {@code Long} 形式；未设置或非数值时返回 {@code null}
   */
  public static Long getTenantIdAsLong() {
    String tenantId = TENANT_ID_CONTEXT.get();
    if (tenantId == null || tenantId.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(tenantId.trim());
    } catch (NumberFormatException e) {
      // 非数值型租户编码（如 "system"）无法转为 Long，调用方应改用 getTenantId()
      return null;
    }
  }

  /** 清除租户上下文（防止内存泄漏）。 */
  public static void clear() {
    TENANT_ID_CONTEXT.remove();
  }
}
