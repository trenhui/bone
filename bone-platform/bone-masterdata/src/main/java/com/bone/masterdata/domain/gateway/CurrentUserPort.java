package com.bone.masterdata.domain.gateway;

/**
 * 当前登录用户出站端口（SoD 校验 / 审计字段回填 / 参考数据 overlay 写路径分发的数据源）。
 *
 * <p>租户取值约定：平台管理员（tenant_id=0）返回 {@code 0L}；参考数据服务据此把「平台目录写」与「租户私有值写」 分发到不同聚合（{@code
 * mdm_reference_value} vs {@code mdm_reference_value_tenant}）。
 */
public interface CurrentUserPort {

  /** 取当前登录账号 ID；无登录上下文（如系统任务）返回 null。 */
  Long currentUserId();

  /** 取当前登录账号 ID；缺失即抛（写操作必须可追溯时使用）。 */
  Long requireUserId();

  /** 取当前租户 ID（JWT claim）；无登录上下文返回 null，平台管理员为 0。 */
  Long currentTenantId();

  /** 取当前租户 ID；缺失即抛（overlay 写路径必须显式分发，不允许无租户上下文的写入）。 */
  Long requireTenantId();
}
