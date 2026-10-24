package com.bone.aitrace.common;

/**
 * bone-ai-trace 稳定错误码（前端 i18n 键 + 监控聚合口径，已登记 Bone-错误码登记.md §6 与两份语言包）。
 *
 * <p><b>为何从 {@code EvidenceTraceListener} 收敛到这里（2026-10-18）</b>：i18n 一致性门禁只扫描 {@code
 * *ErrorCodes.java} 类，码定义在监听器私有常量里时，台账与代码字符串的一致性实际未被校验（假安全）。收敛到本类让门禁扫得到。
 */
public final class AiTraceErrorCodes {

  private AiTraceErrorCodes() {}

  /**
   * 证据链落盘拒绝：租户上下文缺失或非数值编码（500）。
   *
   * <p>fail-closed——「落一条租户不明的合规证据」比「拒返」更危险（审计链上出现无法归属的快照即失真）。
   */
  public static final String TENANT_CONTEXT_MISSING = "AI_TRACE_TENANT_CONTEXT_MISSING";

  /** 证据回放：快照不存在（404，REST 回放面，2026-10-23 随 TraceController 落地）。 */
  public static final String NOT_FOUND = "AI_TRACE_NOT_FOUND";

  /**
   * 证据回放：快照不属于当前租户（403，REST 回放面，2026-10-23 随 TraceController 落地）。
   *
   * <p>跨租户命中显式拒绝而非静默返空——「查不到」与「不许查」必须可区分（取证与排障都依赖这个区分）。
   */
  public static final String TENANT_DENIED = "AI_TRACE_TENANT_DENIED";
}
