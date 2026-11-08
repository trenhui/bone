package com.bone.claim.application;

import com.bone.claim.application.port.out.AiCapabilityDeclaration;
import com.bone.claim.application.port.out.AiGatewayPort;
import com.bone.claim.application.port.out.GraphFraudPort;
import com.bone.claim.application.port.out.KnowledgeRetrievalPort;
import com.bone.claim.application.port.out.RuleEnginePort;
import com.bone.claim.application.port.out.VisualForensicsPort;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI 能力清单与自动化就绪度（只读聚合，监管 §15「制定应用清单，实施分级管控」+ §17「明确紧急停用及模型退出条件」）。
 *
 * <p><b>本服务刻意不做的事</b>：不写库、不调用任何能力的业务方法、不改开关、不参与金额或资金状态判定。
 * 它只把各出站适配器<b>自报</b>的部署态声明与自动放款总开关聚合成一份可查询清单，回答「当前开不出自动放款， 具体卡在哪几道闸门」——这正是此前只能靠读源码 / 翻注释才能得到的信息。
 *
 * <p><b>三层必须分清（防假绿的核心）</b>：
 *
 * <ol>
 *   <li><b>部署态</b>（本清单）：适配器有没有真实实现；由 {@code declaration()} 自报；
 *   <li><b>配置态</b>（本清单）：{@code bone.claim.auto-approve.enabled} 是否开启；
 *   <li><b>运行期</b>（<b>不在本清单</b>）：本次案件是否真的执行了各能力，见决策轨迹的 {@code capabilityMissing / capabilityReason
 *       / fraudGateReason / triageReason} 与 {@code t_claim_rule_hit}。
 * </ol>
 *
 * <p>即使 {@code deploymentGatesPassed=true}，也<b>不代表任何案件可以自动放款</b>：还须逐案运行期 {@code
 * available=true}、确定性分诊通过、大额双人复核规则不触发，以及资金策略的正式签核（§16 承保理赔属 高风险应用，须风险管理委员会批准）。清单把这句话写进 {@link
 * #SCOPE_NOTE} 随响应下发，避免被当成放款许可。
 */
@Service
@RequiredArgsConstructor
public class AiCapabilityReadinessService {

  /** 风险等级：§16 明确「承保理赔」属高风险 AI 应用，清单整体按 HIGH 登记。 */
  public static final String RISK_TIER_HIGH = "HIGH";

  /** 随每次响应下发的口径声明，禁止消费方把清单误读成逐案结论或放款许可。 */
  public static final String SCOPE_NOTE =
      "部署态清单（适配器自报 + 配置态开关），不代表任何具体案件已执行该能力；"
          + "运行期证据见决策轨迹 capabilityMissing/capabilityReason/fraudGateReason/triageReason 与 t_claim_rule_hit。"
          + "deploymentGatesPassed 仅表示部署态闸门不再阻断，不构成放款许可："
          + "还须逐案运行期 available=true、确定性分诊通过、大额双人复核规则不触发，"
          + "以及 §16 风险管理委员会批准与 §17 人工干预/紧急停用机制就绪。";

  private final RuleEnginePort ruleEnginePort;
  private final VisualForensicsPort visualForensicsPort;
  private final GraphFraudPort graphFraudPort;
  private final KnowledgeRetrievalPort knowledgeRetrievalPort;
  private final AiGatewayPort aiGatewayPort;
  private final AutoApprovalApplicationService autoApprovalApplicationService;

  /** 治理清单快照（不可变，字段均为值对象 / 受控码，不含自由文本结论）。 */
  public record ReadinessSnapshot(
      String riskTier,
      boolean autoApproveEnabled,
      List<AiCapabilityDeclaration> capabilities,
      List<String> blockers,
      boolean deploymentGatesPassed,
      String scopeNote) {}

  /**
   * 聚合当前部署态清单。
   *
   * <p>阻断项按「先配置闸、后能力闸」稳定排序，便于运维按序处置；每项都由受控原因码构成， 不拼接模型输出或请求参数（避免自由文本进入审计面）。
   */
  public ReadinessSnapshot snapshot() {
    List<AiCapabilityDeclaration> capabilities =
        List.of(
            safe(ruleEnginePort::declaration, "C2_RULE"),
            safe(visualForensicsPort::declaration, "E1_VISUAL"),
            safe(graphFraudPort::declaration, "E2_GRAPH"),
            safe(knowledgeRetrievalPort::declaration, "E3_KNOWLEDGE"),
            safe(aiGatewayPort::declaration, "C5_LLM"));

    boolean enabled = autoApprovalApplicationService.isAutoApproveEnabled();
    List<String> blockers = new ArrayList<>();
    if (!enabled) {
      blockers.add("AUTO_APPROVE_TOGGLE_OFF");
    }
    for (AiCapabilityDeclaration capability : capabilities) {
      if (!capability.real()) {
        blockers.add(
            capability.capabilityId() + ":" + capability.state() + ":" + capability.reason());
      }
    }
    return new ReadinessSnapshot(
        RISK_TIER_HIGH,
        enabled,
        capabilities,
        List.copyOf(blockers),
        blockers.isEmpty(),
        SCOPE_NOTE);
  }

  /**
   * 声明缺失兜底：Mockito 桩 / 未覆盖 {@code declaration()} 的实现会返回 {@code null} 或 UNKNOWN， 一律按
   * UNKNOWN（fail-closed）处理，绝不因「拿不到声明」而假设能力已就绪。
   */
  private static AiCapabilityDeclaration safe(
      Supplier<AiCapabilityDeclaration> declaration, String capabilityId) {
    AiCapabilityDeclaration value;
    try {
      value = declaration.get();
    } catch (RuntimeException ex) {
      return AiCapabilityDeclaration.unknown(capabilityId);
    }
    return value == null ? AiCapabilityDeclaration.unknown(capabilityId) : value;
  }
}
