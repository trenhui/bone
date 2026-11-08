package com.bone.claim.infrastructure.ai;

import com.bone.claim.application.port.out.AiGatewayPort;
import com.bone.claim.application.port.out.AiGatewayPort.Fact;
import com.bone.claim.application.port.out.AiGatewayUnavailableException;
import com.bone.claim.application.port.out.ClaimContextPort.ClaimContextView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * AI 评估网关适配器（一期本地确定性桩，仅本地 / 演示用）。
 *
 * <p>默认不启用：Phase B 起 {@link com.bone.claim.infrastructure.client.AiGatewayPortAdapter} 为默认实现，委托
 * bone-ai C5 网关（含 PII 脱敏 / 注入检测 / 内容安全 / 证据链）。 本桩仅当显式设置 {@code synap.claim.ai.gateway.mode=local}
 * 时生效——用于无需网关依赖的纯本地开发 / 回归基线。
 *
 * <p><b>为什么必须标 {@code @Primary}</b>：默认实现是无条件的 {@code @Component}，本桩在 {@code mode=local} 下同样生效， 两个
 * {@code AiGatewayPort} 会同时注册；而注入点（{@code AiAssessNodeHandler}）按类型注入且无 {@code @Qualifier}， 没有
 * {@code @Primary} 时会抛 {@code NoUniqueBeanDefinitionException}——即"本地模式开关一打开，应用就起不来"，
 * 恰是这个开关唯一存在的场景。{@code @Primary} 让 local 模式确定性选中本桩，且该注解在属性缺席时无任何影响。
 *
 * <p>启用时默认可用：返回置信度 0.93、不建议拒赔；{@code setEnabled(false)} 模拟网关故障， 使 {@link #assess} 抛 {@link
 * AiGatewayUnavailableException} 触发决策图 LLM 节点降级（FALLBACK）。
 *
 * <p><b>v1.3 AI 原生（本地桩对齐契约）</b>：本地模式虽无真实模型与单证 IR，仍从<b>已有的结构化上下文</b>确定性派生最小认知（{@code facts}：案件类型 /
 * 金额 / 证据齐备度 / 最低 OCR 置信度），使演示决策轨迹的 {@code aiFacts} 非空、AI 事实卡片可展示。{@code clauseRefs} / {@code
 * evidenceRefs} / {@code discrepancies} 需单证解析结果（PDF 条款切片 /
 * span），本地桩无此数据，保持空列表——<b>不伪造</b>认知来源，真实结构化认知由 Phase B 的 C5 网关与 C6 受控 RAG 回填。
 */
@Component
@Primary
@ConditionalOnProperty(
    name = "synap.claim.ai.gateway.mode",
    havingValue = "local",
    matchIfMissing = false)
public class LocalAiGatewayPortAdapter implements AiGatewayPort {

  private static final BigDecimal AI_CONFIDENCE = new BigDecimal("0.93");

  /** 本地桩自报的模型标识：显式标注"非真实模型"，避免溯源字段留空被误读为"已接入真实模型"。 */
  private static final String LOCAL_MODEL = "local-stub";

  private static final String LOCAL_PROMPT_VERSION = "local-stub-v1";

  private volatile boolean enabled = true;

  /** 测试/演练用：模拟网关可用性。 */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public AiAssessment assess(ClaimContextView ctx) {
    if (!enabled) {
      throw new AiGatewayUnavailableException("AI 网关不可用（本地桩关闭）");
    }
    return new AiAssessment(
        true,
        false,
        AI_CONFIDENCE,
        List.of(),
        LOCAL_MODEL,
        LOCAL_PROMPT_VERSION,
        deriveLocalFacts(ctx),
        List.of(), // 无单证 IR：不伪造证据定位
        List.of(), // 无单证 IR：不伪造条款引用
        List.of()); // 无真实模型评估：不伪造差异提示
  }

  /**
   * 从结构化上下文确定性派生最小认知事实（感知层结构化抽取，非模型推断）。
   *
   * <p>本地桩无单证 IR、无模型，只把<b>决策图已持有的案件上下文</b>转写为受控码事实，使 AI 事实卡片在演示中非空。 这些事实只进决策轨迹供展示与留痕，
   * <b>不参与金额、不产拒赔结论、不改分诊</b>（P1 不变）。
   */
  private static List<Fact> deriveLocalFacts(ClaimContextView ctx) {
    List<Fact> facts = new ArrayList<>();
    String claimRef = "claim-" + ctx.claimId() + "#context";
    facts.add(new Fact("CLAIM_TYPE", nullToEmpty(ctx.claimType()), claimRef));
    facts.add(
        new Fact(
            "CLAIM_AMOUNT",
            ctx.claimAmount() == null ? "" : ctx.claimAmount().toPlainString(),
            claimRef));
    String evidenceStatus =
        (ctx.hasEvidence() ? "PRESENT" : "MISSING") + (ctx.evidenceAllParsed() ? "" : "/UNPARSED");
    facts.add(new Fact("EVIDENCE_STATUS", evidenceStatus, "claim-" + ctx.claimId() + "#evidence"));
    if (ctx.minOcrConfidence() != null) {
      facts.add(
          new Fact(
              "MIN_OCR_CONFIDENCE",
              ctx.minOcrConfidence().toPlainString(),
              "claim-" + ctx.claimId() + "#ocr"));
    }
    if (!ctx.evidenceAllVerifyPassed()) {
      facts.add(
          new Fact("EVIDENCE_VERIFY", "NOT_ALL_PASSED", "claim-" + ctx.claimId() + "#verify"));
    }
    return List.copyOf(facts);
  }

  private static String nullToEmpty(String s) {
    return s == null ? "" : s;
  }

  @Override
  public com.bone.claim.application.port.out.AiCapabilityDeclaration declaration() {
    return com.bone.claim.application.port.out.AiCapabilityDeclaration.stub(
        "C5_LLM", "本地确定性桩：未接真实 LLM provider", "配置 bone.ai.gateway.base-url 接入真实 provider");
  }
}
