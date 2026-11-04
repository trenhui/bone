package com.bone.claim.application.port.out;

/**
 * 部署态 AI 能力声明（监管 §15「制定应用清单，实施分级管控」的最小技术承载）。
 *
 * <p><b>与运行期 {@code available()} 严格区分，不可互相替代</b>：
 *
 * <ul>
 *   <li>{@code declaration()} 是<b>部署态</b>：本进程装配的适配器自报「有没有真实实现」，用于应用清单、分级管控、
 *       紧急停用路径登记，以及回答「为什么当前开不出自动放款」；
 *   <li>{@code RuleCheckResult.available()} / {@code KnowledgeResult.available()} 等是<b>运行期</b>：
 *       <b>本次案件是否真的执行了</b>该能力，随决策轨迹落库，是审计证据。
 * </ul>
 *
 * <p>二者不一致是合法的（例如声明 READY 但本次调用超时降级为 FALLBACK），因此清单<b>绝不</b>用来推断某个案件 「已核责 /
 * 已反欺诈」，也绝不参与任何金额或资金状态判定。这一区分是针对「能力未集成被当成无风险」这一类 假绿缺陷的护栏：声明缺失时一律回落 {@link #STATE_UNKNOWN}，而不是回落
 * READY。
 *
 * <p><b>为什么用端口 default 方法而不是外部配置表</b>：声明与实现同处一个文件，接线时改实现必须同时改声明；
 * 外部配置表会与真实装配漂移，形成「清单说已接、代码其实还是桩」的第二真源。未覆盖声明的实现一律判 UNKNOWN（fail-closed 方向），并由 {@code
 * AiCapabilityReadinessServiceTest} 的一致性用例兜住漏报。
 */
public record AiCapabilityDeclaration(
    String capabilityId, String state, String reason, String remediation) {

  /** 已接入真实实现（仍不代表本次案件已执行）。 */
  public static final String STATE_READY = "READY";

  /** 一期本地桩：方法可调用但未执行真实能力，结果恒 {@code available=false}。 */
  public static final String STATE_LOCAL_STUB = "LOCAL_STUB";

  /** 适配器已装配但关键外部配置缺失（如网关 base-url 未配），实际会抛降级异常。 */
  public static final String STATE_NOT_CONFIGURED = "NOT_CONFIGURED";

  /** 适配器未自报声明：不得假设为可用。 */
  public static final String STATE_UNKNOWN = "UNKNOWN";

  /** 归一化：空值不落 null，避免清单出现「null 字符串」与下游 NPE。 */
  public AiCapabilityDeclaration {
    capabilityId = capabilityId == null ? "" : capabilityId;
    state = state == null || state.isBlank() ? STATE_UNKNOWN : state;
    reason = reason == null ? "" : reason;
    remediation = remediation == null ? "" : remediation;
  }

  /** 未自报声明的兜底（fail-closed）。 */
  public static AiCapabilityDeclaration unknown(String capabilityId) {
    return new AiCapabilityDeclaration(
        capabilityId, STATE_UNKNOWN, "DECLARATION_MISSING", "适配器须覆盖 declaration() 自报部署态状态");
  }

  /** 本地桩声明。 */
  public static AiCapabilityDeclaration stub(
      String capabilityId, String reason, String remediation) {
    return new AiCapabilityDeclaration(capabilityId, STATE_LOCAL_STUB, reason, remediation);
  }

  /** 真实实现声明。 */
  public static AiCapabilityDeclaration ready(String capabilityId, String remediation) {
    return new AiCapabilityDeclaration(capabilityId, STATE_READY, "", remediation);
  }

  /** 已装配但未配置真实上游。 */
  public static AiCapabilityDeclaration notConfigured(
      String capabilityId, String reason, String remediation) {
    return new AiCapabilityDeclaration(capabilityId, STATE_NOT_CONFIGURED, reason, remediation);
  }

  /** 是否声明为真实实现。仅用于清单与阻断归因，<b>不得</b>作为放款判据。 */
  public boolean real() {
    return STATE_READY.equals(state);
  }
}
