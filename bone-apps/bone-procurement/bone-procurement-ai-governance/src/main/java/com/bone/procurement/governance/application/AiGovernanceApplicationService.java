package com.bone.procurement.governance.application;

import com.bone.procurement.governance.domain.gateway.CapabilityRegistry;
import com.bone.procurement.governance.domain.gateway.GovernanceActorPort;
import com.bone.procurement.governance.domain.gateway.GovernanceTracePort;
import com.bone.procurement.governance.domain.gateway.HumanDecisionLedger;
import com.bone.procurement.governance.domain.model.capability.AiCapability;
import com.bone.procurement.governance.domain.model.capability.CapabilityCode;
import com.bone.procurement.governance.domain.model.capability.CapabilityNotFoundException;
import com.bone.procurement.governance.domain.model.capability.DriftSignal;
import com.bone.procurement.governance.domain.model.capability.DriftThresholds;
import com.bone.procurement.governance.domain.model.capability.GovernanceDecision;
import com.bone.procurement.governance.domain.model.capability.GovernanceTraceEntry;
import com.bone.procurement.governance.domain.model.capability.HumanFinalDecision;
import com.bone.procurement.governance.domain.service.CapabilityGovernancePolicy;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * AI 决策治理应用服务：能力登记 / 一键停用降级 / 准入裁决 / 人工终决 + override 回流四组用例。
 *
 * <p>红线：
 *
 * <ul>
 *   <li>准入裁决一律 fail-closed——未登记 / 不可判定 / 停用 / 降级均拒绝，仅 AVAILABLE 放行且仅作建议；
 *   <li>人工终决权不可绕过——复核人与治理操作人一律取登录态、不接受自报，缺登录态直接拒绝；override 记录强制具名复核人与耗时，AI 结论与终决不一致时触发评测增补标记；
 *   <li>所有状态变更与终决写入追溯链；不保存 AI output 原文（PII 禁入）。
 * </ul>
 */
@Service
@ConditionalOnAiGovernanceEnabled
public class AiGovernanceApplicationService {

  private final CapabilityRegistry registry;
  private final HumanDecisionLedger ledger;
  private final GovernanceTracePort tracePort;
  private final GovernanceActorPort actorPort;
  private final CapabilityGovernancePolicy policy = new CapabilityGovernancePolicy();

  public AiGovernanceApplicationService(
      CapabilityRegistry registry,
      HumanDecisionLedger ledger,
      GovernanceTracePort tracePort,
      GovernanceActorPort actorPort) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.ledger = Objects.requireNonNull(ledger, "ledger");
    this.tracePort = Objects.requireNonNull(tracePort, "tracePort");
    this.actorPort = Objects.requireNonNull(actorPort, "actorPort");
  }

  /** 用例一：登记能力（初始 UNKNOWN，不自动启用）；责任人 userId 由命令提供，登记动作操作人取登录态。 */
  public CapabilityView register(RegisterCapabilityCommand command) {
    String operator = requireActor();
    CapabilityCode code = new CapabilityCode(command.code());
    if (registry.exists(code)) {
      throw new IllegalArgumentException("能力已登记: " + code.value());
    }
    AiCapability capability = AiCapability.register(code, command.displayName(), command.owner());
    registry.save(capability);
    tracePort.append(
        GovernanceTraceEntry.of(
            code.value(),
            "REGISTERED",
            "operator=" + operator + ", owner=" + command.ownerUserId()));
    return CapabilityView.from(capability);
  }

  /** 用例二：准入裁决（fail-closed）。供各 AI 能力在推理前调用。 */
  public GovernanceDecision decide(String codeValue) {
    CapabilityCode code = new CapabilityCode(codeValue);
    AiCapability capability = registry.find(code).orElse(null);
    GovernanceDecision decision =
        capability == null ? policy.denyUnknown(code) : policy.decide(capability);
    tracePort.append(
        GovernanceTraceEntry.of(
            code.value(),
            "DECISION",
            "allowed=" + decision.allowed() + ", reason=" + decision.reasonCode()));
    return decision;
  }

  /** 用例三：一键停用 / 降级 / 启用 / 标记不可判定。操作人取登录态。 */
  public CapabilityView changeState(String action, ChangeCapabilityStateCommand command) {
    String operator = requireActor();
    AiCapability capability = requireCapability(command.code());
    switch (action) {
      case "enable" -> capability.enable(operator, command.reason());
      case "disable" -> capability.disable(operator, command.reason());
      case "degrade" -> capability.degrade(operator, command.reason());
      case "undetermined" -> capability.markUndetermined(operator, command.reason());
      default -> throw new IllegalArgumentException("未知治理动作: " + action);
    }
    registry.save(capability);
    // 治理审计要求所有状态变更进入中央追溯链（聚合内部亦有一份不可变留痕，两者互补）。
    tracePort.append(
        GovernanceTraceEntry.of(
            capability.code().value(),
            switch (action) {
              case "enable" -> "ENABLED";
              case "disable" -> "DISABLED";
              case "degrade" -> "DEGRADED";
              case "undetermined" -> "UNDETERMINED";
              default -> "STATE_CHANGED";
            },
            "operator=" + operator + ", reason=" + command.reason()));
    return CapabilityView.from(capability);
  }

  /** 用例四：人工终决 + override 回流台账。复核人取登录态、不接受自报。 */
  public HumanDecisionView recordHumanDecision(RecordHumanDecisionCommand command) {
    String reviewer = requireActor();
    CapabilityCode code = new CapabilityCode(command.capabilityCode());
    boolean isOverride =
        command.aiConclusion() != null && !command.aiConclusion().equals(command.finalConclusion());
    HumanFinalDecision decision =
        new HumanFinalDecision(
            command.decisionId() == null || command.decisionId().isBlank()
                ? "hfd-" + UUID.randomUUID()
                : command.decisionId(),
            code,
            command.subjectRef(),
            command.aiConclusion(),
            command.aiConfidence(),
            command.finalConclusion(),
            reviewer,
            command.reason(),
            command.reviewDurationMs(),
            /* overrideAccepted= */ true,
            /* evaluationFeedbackRaised= */ isOverride,
            Instant.now());
    ledger.append(decision);
    tracePort.append(
        GovernanceTraceEntry.of(
            code.value(),
            isOverride ? "HUMAN_OVERRIDE" : "HUMAN_FINAL_DECISION",
            "decisionId=" + decision.decisionId() + ", reviewer=" + reviewer));
    return HumanDecisionView.from(decision);
  }

  /** 查询：能力清单。 */
  public List<CapabilityView> listCapabilities() {
    return registry.listAll().stream().map(CapabilityView::from).toList();
  }

  /** 查询：单个能力。 */
  public CapabilityView getCapability(String codeValue) {
    return CapabilityView.from(requireCapability(codeValue));
  }

  /** 查询：override 回流计数（drift 告警输入）。 */
  public long countOverrides(String codeValue) {
    return ledger.countOverrides(new CapabilityCode(codeValue));
  }

  /**
   * 查询：漂移信号（只读，fail-closed）。
   *
   * <p>样本数取追溯链中人工终决事件数（每次终决都会留痕），推翻数取台账真实计数；阈值未配置 / 样本不足 / 计数非法一律
   * UNDETERMINED。本方法不写追溯链、不改能力状态、不声明模型效果达标。
   */
  public DriftSignal driftSignal(String codeValue, DriftThresholds thresholds) {
    CapabilityCode code = new CapabilityCode(codeValue);
    if (!registry.exists(code)) {
      throw new CapabilityNotFoundException(code.value());
    }
    long sampleSize =
        tracePort.listByCapability(code.value()).stream()
            .filter(
                entry ->
                    "HUMAN_FINAL_DECISION".equals(entry.eventType())
                        || "HUMAN_OVERRIDE".equals(entry.eventType()))
            .count();
    long overrideCount = ledger.countOverrides(code);
    return DriftSignal.evaluate(code.value(), sampleSize, overrideCount, thresholds, Instant.now());
  }

  /** 审计追溯：按能力码查留痕。 */
  public List<GovernanceTraceEntry> trace(String codeValue) {
    return tracePort.listByCapability(codeValue);
  }

  /** 当前具名责任人（供披露页展示治理归属）。 */
  public String currentOwner(String codeValue) {
    return requireCapability(codeValue).owner().displayName();
  }

  private AiCapability requireCapability(String codeValue) {
    CapabilityCode code = new CapabilityCode(codeValue);
    return registry.find(code).orElseThrow(() -> new CapabilityNotFoundException(code.value()));
  }

  /** 无登录态一律 fail-closed：治理操作与人工终决都不可匿名执行。 */
  private String requireActor() {
    String actor = actorPort.currentActorId();
    if (actor == null || actor.isBlank()) {
      throw new IllegalStateException("需要登录态操作人，治理操作与人工终决不接受匿名/自报");
    }
    return actor;
  }
}
