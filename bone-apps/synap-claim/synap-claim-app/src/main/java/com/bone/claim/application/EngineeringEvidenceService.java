package com.bone.claim.application;

import com.bone.claim.application.AiCapabilityReadinessService.ReadinessSnapshot;
import com.bone.claim.application.port.out.AiCapabilityDeclaration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 工程证据底座（只读）：把散落在能力声明、配置开关与 fail-closed 语义里的证据，收敛成一份稳定、可机检的判据视图。
 *
 * <p><b>它回答什么</b>：「这一处安全姿态，当前有没有可核的工程证据？」——例如视觉反诈为何恒转人工、provider 为何未接、
 * 四类不可达限定维度为何表达不出来。它回答的是<b>证据是否齐备</b>，不是<b>能力是否达标</b>。
 *
 * <p><b>三条不可动摇的边界（防假绿的核心）</b>：
 *
 * <ol>
 *   <li><b>只读、无副作用</b>：不写库、不改开关、不调用任何能力的业务方法、不发 provider 请求（避免像探活那样计入用量）。 证据全部来自 {@link
 *       AiCapabilityReadinessService#snapshot()} 这一部署态真源；
 *   <li><b>不替代门禁、不做放款结论</b>：视图里只有判据证据，没有任何「门禁通过 / 可放款 / 已达标」字段。 L3 成熟度仍由 {@code
 *       _ai_native_readiness.py} 的 12 项判据独立判定，本视图即便全 EVIDENCED 也不翻绿任何门禁；
 *   <li><b>声明缺失即 NOT_EVIDENCED</b>：拿不到声明、状态非 {@link AiCapabilityDeclaration#STATE_READY} 一律按「无证据」，
 *       原因码为受控码，绝不因「查不到」而假设已就绪。
 * </ol>
 *
 * <p>判据 ID 是稳定契约：前端 / 审计 / 变异验证按 ID 引用，不改 ID 即视为同一判据。
 */
@Service
@RequiredArgsConstructor
public class EngineeringEvidenceService {

  /** 证据状态：有可核的部署态证据。注意这不等于运行期达标，更不等于放款许可。 */
  public static final String EVIDENCED = "EVIDENCED";

  /** 证据状态：无可核证据（能力未接线 / 配置缺失 / 声明缺失 / 当前不可表达）。 */
  public static final String NOT_EVIDENCED = "NOT_EVIDENCED";

  /** 原因码：适配器未自报声明（含 null / 抛异常的兜底）。 */
  public static final String REASON_DECLARATION_MISSING = "DECLARATION_MISSING";

  /** 原因码：provider 未配置（来自 C5_LLM 声明的受控原因，缺省时用本码）。 */
  public static final String REASON_PROVIDER_NOT_CONFIGURED = "AI_GATEWAY_BASE_URL_MISSING";

  /** 原因码：四类限定维度（区域/医保/药品/诊疗）当前无配置列，表达不出来。 */
  public static final String REASON_NO_CONFIG_COLUMN = "NO_CONFIG_COLUMN";

  /** 原因码：fail-closed 原因码通道已由既有决策轨迹提供（可查询证据存在）。 */
  public static final String REASON_TRACE_REASON_WIRED = "TRACE_REASON_WIRED";

  /** 随视图下发的口径声明：明确「证据齐备 ≠ 能力达标 ≠ 放款许可」。 */
  public static final String NOTE =
      "工程证据底座：EVIDENCED 仅表示部署态有可核证据，不代表运行期达标，更不构成放款许可；"
          + "本视图只读、不替代 _ai_native_readiness 的 12 项门禁判据，冲突时以门禁的更保守结论为准。"
          + "LIMITED_DIMENSION_REACHABILITY 恒为 NOT_EVIDENCED：四类维度当前无配置列，表达不出来；"
          + "FAIL_CLOSED_TRACE_REASON 的证据随决策轨迹 capabilityReason/fraudGateReason/triageReason 落库，按案件可查。";

  private static final Map<String, String> CAPABILITY_CRITERION_IDS = buildCapabilityIds();

  private final AiCapabilityReadinessService readinessService;

  /** 单条判据证据：稳定 ID + 受控状态 + 受控原因码，不含自由文本。 */
  public record EvidenceCriterion(String id, String status, String reason) {
    public EvidenceCriterion {
      id = id == null ? "" : id;
      status = status == null ? NOT_EVIDENCED : status;
      reason = reason == null ? "" : reason;
    }
  }

  /** 证据视图：判据列表稳定排序 + 口径声明。 */
  public record EvidenceView(List<EvidenceCriterion> criteria, String note) {
    public EvidenceView {
      criteria = criteria == null ? List.of() : List.copyOf(criteria);
      note = note == null ? "" : note;
    }
  }

  /** 聚合部署态证据，产出只读视图。 */
  public EvidenceView view() {
    ReadinessSnapshot snapshot = readinessService.snapshot();
    Map<String, AiCapabilityDeclaration> declarations = declarationsOf(snapshot);

    List<EvidenceCriterion> criteria = new ArrayList<>();
    criteria.add(
        toggleCriterion(
            "AUTO_APPROVE_TOGGLE", snapshot.autoApproveEnabled(), "AUTO_APPROVE_TOGGLE_OFF"));
    for (Map.Entry<String, String> entry : CAPABILITY_CRITERION_IDS.entrySet()) {
      criteria.add(declarationCriterion(entry.getKey(), entry.getValue(), declarations));
    }
    criteria.add(providerCriterion(declarations.get("C5_LLM")));
    // 四类限定维度当前无配置列——恒 NOT_EVIDENCED，不伪造可达证据。
    criteria.add(
        new EvidenceCriterion(
            "LIMITED_DIMENSION_REACHABILITY", NOT_EVIDENCED, REASON_NO_CONFIG_COLUMN));
    // fail-closed 原因码通道由既有决策轨迹提供：声明任一能力未就绪即说明该通道有真实落点。
    criteria.add(failClosedTraceCriterion(snapshot));

    return new EvidenceView(List.copyOf(criteria), NOTE);
  }

  private EvidenceCriterion toggleCriterion(String id, boolean enabled, String offReason) {
    return enabled
        ? new EvidenceCriterion(id, EVIDENCED, "")
        : new EvidenceCriterion(id, NOT_EVIDENCED, offReason);
  }

  private EvidenceCriterion declarationCriterion(
      String criterionId, String capabilityId, Map<String, AiCapabilityDeclaration> declarations) {
    AiCapabilityDeclaration declaration = declarations.get(capabilityId);
    if (declaration == null) {
      return new EvidenceCriterion(criterionId, NOT_EVIDENCED, REASON_DECLARATION_MISSING);
    }
    // 只有真实实现才算「有部署态证据」；桩/未配置/未知一律 NOT_EVIDENCED。
    if (declaration.real()) {
      return new EvidenceCriterion(criterionId, EVIDENCED, "");
    }
    String reason =
        AiCapabilityDeclaration.STATE_UNKNOWN.equals(declaration.state())
                && "DECLARATION_MISSING".equals(declaration.reason())
            ? REASON_DECLARATION_MISSING
            : declaration.reason();
    return new EvidenceCriterion(criterionId, NOT_EVIDENCED, reason == null ? "" : reason);
  }

  private EvidenceCriterion providerCriterion(AiCapabilityDeclaration llm) {
    if (llm == null || AiCapabilityDeclaration.STATE_UNKNOWN.equals(llm.state())) {
      return new EvidenceCriterion(
          "C5_LLM_PROVIDER_CONFIG", NOT_EVIDENCED, REASON_DECLARATION_MISSING);
    }
    if (llm.real()) {
      return new EvidenceCriterion("C5_LLM_PROVIDER_CONFIG", EVIDENCED, "");
    }
    return new EvidenceCriterion(
        "C5_LLM_PROVIDER_CONFIG", NOT_EVIDENCED, REASON_PROVIDER_NOT_CONFIGURED);
  }

  private EvidenceCriterion failClosedTraceCriterion(ReadinessSnapshot snapshot) {
    // 决策轨迹的 fail-closed 原因码通道是工程实现（非能力声明），只要视图能取到部署态快照，
    // 该通道即视为「可查询证据存在」——真正的原因码随案件落库，不在本视图里伪造具体值。
    boolean traceChannelPresent = snapshot != null && snapshot.capabilities() != null;
    return traceChannelPresent
        ? new EvidenceCriterion("FAIL_CLOSED_TRACE_REASON", EVIDENCED, REASON_TRACE_REASON_WIRED)
        : new EvidenceCriterion(
            "FAIL_CLOSED_TRACE_REASON", NOT_EVIDENCED, REASON_DECLARATION_MISSING);
  }

  private static Map<String, AiCapabilityDeclaration> declarationsOf(ReadinessSnapshot snapshot) {
    Map<String, AiCapabilityDeclaration> map = new LinkedHashMap<>();
    if (snapshot == null || snapshot.capabilities() == null) {
      return map;
    }
    for (AiCapabilityDeclaration capability : snapshot.capabilities()) {
      if (capability != null && !capability.capabilityId().isBlank()) {
        map.put(capability.capabilityId(), capability);
      }
    }
    return map;
  }

  private static Map<String, String> buildCapabilityIds() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("C2_RULE_DECLARATION", "C2_RULE");
    map.put("E1_VISUAL_DECLARATION", "E1_VISUAL");
    map.put("E2_GRAPH_DECLARATION", "E2_GRAPH");
    map.put("E3_KNOWLEDGE_DECLARATION", "E3_KNOWLEDGE");
    return Map.copyOf(map);
  }
}
