package com.bone.integration.common;

/**
 * bone-integration 稳定业务错误码（登记见 {@code doc/architecture/Bone-错误码登记.md} §6 {@code INT_} 段）。
 *
 * <p><b>为何需要这一层</b>：此前本模块用 {@code throw new DomainException("连接器不存在")} —— 前端、监控、告警 只能按中文 message
 * 分类，文案一改聚合口径即断；{@code IntegrationExceptionAdvice} 也只能把它统一兜底成 {@code
 * COMMON_VALIDATION_FAILED}，前端拿不到可 i18n 的 {@code errorCode}。稳定码是跨系统契约，中文明细只是 fallback（错误码登记
 * §2「可聚合」「可 i18n」）。
 *
 * <p><b>用法</b>：抛出走 {@link IntegrationErrors#of(String, Object)}——{@code throw
 * IntegrationErrors.of(IntegrationErrorCodes.FLOW_NOT_FOUND, flowId)}。HTTP 状态由 {@link
 * IntegrationErrors} 的「码 → 状态」表提供，<strong>不要</strong>在抛出点手写状态数字。
 *
 * <p><b>本类只承载稳定的码字符串 + 语义</b>，不承载状态（真源见 {@link IntegrationErrors}），也不承载文案。
 *
 * <p><b>命名</b>：{@code INT_} 为集成模块前缀，格式 {@code {DOMAIN_PREFIX}_{SNAKE_CASE_REASON}}（错误码登记 §3.1）。
 */
public final class IntegrationErrorCodes {

  // ===== 连接器（INT_CONNECTOR_*）=====

  /** 连接器不存在（含跨租户不可见）。 */
  public static final String CONNECTOR_NOT_FOUND = "INT_CONNECTOR_NOT_FOUND";

  /** 同一租户作用域内该连接器名称已存在。 */
  public static final String CONNECTOR_NAME_CONFLICT = "INT_CONNECTOR_NAME_CONFLICT";

  /** 连接器类型不受支持（无对应的 ExternalSystemClient 实现）。 */
  public static final String CONNECTOR_TYPE_UNSUPPORTED = "INT_CONNECTOR_TYPE_UNSUPPORTED";

  /** 连接器能力未实现：协议客户端为 501 占位，禁止假成功（INT-01）。 */
  public static final String CONNECTOR_NOT_IMPLEMENTED = "INT_CONNECTOR_NOT_IMPLEMENTED";

  // ===== 流程（INT_FLOW_*）=====

  /** 集成流程不存在（含跨租户不可见）。 */
  public static final String FLOW_NOT_FOUND = "INT_FLOW_NOT_FOUND";

  /** 同一租户作用域内该流程名称已存在。 */
  public static final String FLOW_NAME_CONFLICT = "INT_FLOW_NAME_CONFLICT";

  /** 流程未激活：执行等动作要求流程处于 ACTIVE 状态。 */
  public static final String FLOW_NOT_ACTIVE = "INT_FLOW_NOT_ACTIVE";

  /** 流程节点为空。 */
  public static final String FLOW_NODES_EMPTY = "INT_FLOW_NODES_EMPTY";

  /** 流程缺少开始节点。 */
  public static final String FLOW_START_NODE_MISSING = "INT_FLOW_START_NODE_MISSING";

  /** 流程缺少结束节点。 */
  public static final String FLOW_END_NODE_MISSING = "INT_FLOW_END_NODE_MISSING";

  // ===== 执行记录（INT_EXECUTION_*）=====

  /** 执行记录 / 执行日志不存在（含跨租户不可见）。 */
  public static final String EXECUTION_NOT_FOUND = "INT_EXECUTION_NOT_FOUND";

  private IntegrationErrorCodes() {}
}
