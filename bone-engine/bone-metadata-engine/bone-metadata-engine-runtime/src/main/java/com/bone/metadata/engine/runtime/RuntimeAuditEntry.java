package com.bone.metadata.engine.runtime;

/**
 * 记录级变更审计条目（RC-009 · Must）。
 *
 * <p><b>为什么必须单独建条目类型，而不是复用动作执行日志</b>：动作日志只覆盖「走了动作端点」的变更； 而审计要能回答「谁把哪张单据从 APPROVED 改成 PAID」，包括软删 /
 * 恢复 / 平台管理员修复直写 （Runtime 数据面设计 §7.1：没有记录级审计的状态机是不完整的状态机）。
 *
 * <p>本类是 engine 侧契约；落库实现由宿主应用注入 {@link RuntimeAuditSink}（避免 engine 依赖具体表）。
 */
public record RuntimeAuditEntry(
    long tenantId,
    String entityCode,
    String recordId,
    /** 被变更的字段 code（受保护状态字段 / 软删时为 {@code deleted}）。 */
    String field,
    String fromValue,
    String toValue,
    String operatorId,
    /** 变更原因；{@code source=admin-fix} 时必填。 */
    String reason,
    /**
     * 来源：{@code action}（状态机动作）/ {@code api} / {@code admin-fix} / {@code delete} / {@code restore}。
     */
    String source,
    /** 关联的动作码（source=action 时有值）。 */
    String actionCode) {

  public static RuntimeAuditEntry of(
      long tenantId,
      String entityCode,
      String recordId,
      String field,
      String fromValue,
      String toValue,
      String operatorId,
      String reason,
      String source,
      String actionCode) {
    return new RuntimeAuditEntry(
        tenantId,
        entityCode,
        recordId,
        field,
        fromValue,
        toValue,
        operatorId,
        reason,
        source,
        actionCode);
  }
}
