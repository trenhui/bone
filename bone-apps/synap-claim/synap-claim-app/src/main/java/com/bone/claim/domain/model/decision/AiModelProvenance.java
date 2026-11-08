package com.bone.claim.domain.model.decision;

import java.util.Locale;
import java.util.Set;

/**
 * 模型来源：这条「AI 结论」到底出自真实的模型调用，还是出自确定性桩。
 *
 * <p><b>为什么需要它</b>：轨迹上的 {@code aiAvailable=true} 只证明「适配器返回了结论」，不证明「模型跑过」。本地确定性桩 （{@code
 * synap.claim.ai.gateway.mode=local}）同样返回 {@code aiAvailable=true}，并在 {@code aiModel} 自报 {@code
 * local-stub}。把两者混同，三处治理口径会同时变好看而事实上没有一个模型参与：
 *
 * <ul>
 *   <li>运行期健康度的参与率（{@code /claim-ops/ai-runtime-health}）——桩环境会显示「AI 100% 在线」；
 *   <li>评测导出的 AI 覆盖率与弱标签一致率（{@code /claims/eval/samples}）——桩的结论会被当成「AI 准确率」；
 *   <li>AI 参与告知——对客户声称「含 AI 辅助生成内容」而实际上没有 AI 参与。
 * </ul>
 *
 * <p><b>为什么按模型标识判定，而不是另加一个自报字段</b>：{@code aiModel} 是既有写侧（{@code AiAssessNodeHandler}）
 * 已经在落的事实，且网关替身与本地桩都必须在此自报身份才能被拒绝/识别；再加一个「我是不是桩」的布尔字段等于让写侧 自己给自己发证，既多一个可能漂移的真源，也仍要为历史行写兜底分支。
 *
 * <p><b>为什么「没报模型」不算生产模型</b>：{@code AiGatewayPortAdapter} 的 {@code resolvedModel()} 保证成功路径
 * 绝不返回空串（缺失时回落逻辑别名），因此「成功但没有模型标识」只可能是老 schema 或异构写入。方向必须保守：证明不了 就不计入参与，与评测侧「SUCCESS 但无模型版本 ⇒
 * UNDETERMINED」同一纪律。
 */
public enum AiModelProvenance {

  /** 可归因到真实上游模型（模型标识存在且不是已知替身）。 */
  PROVIDER,

  /** 确定性桩 / 网关替身：方法被调用过，但没有模型参与。 */
  STUB,

  /** 没有模型标识，无法证明出自哪个模型。 */
  UNKNOWN;

  /**
   * 非生产模型标识（受控集合）。
   *
   * <p>当前只有一期本地桩。新增替身时必须在此登记，并由 {@code LocalAiGatewayPortAdapterTest} 的一致性用例 保证「桩自报的标识一定能被判为
   * STUB」——改桩名却忘了登记会直接红，而不是让桩静默变成「生产模型」。
   */
  private static final Set<String> NON_PRODUCTION_MODEL_IDS = Set.of("local-stub");

  /** 网关内置替身的命名前缀（{@code MockLlmClient} 一类）：同样不是模型。 */
  private static final String MOCK_MODEL_PREFIX = "mock";

  /**
   * 按模型标识判定来源。
   *
   * <p>比对前统一小写去空白：模型标识是外部输入（网关回传），大小写与首尾空白不应改变判定结果。
   */
  public static AiModelProvenance of(String modelId) {
    if (modelId == null || modelId.isBlank()) {
      return UNKNOWN;
    }
    String id = modelId.trim().toLowerCase(Locale.ROOT);
    if (NON_PRODUCTION_MODEL_IDS.contains(id) || id.startsWith(MOCK_MODEL_PREFIX)) {
      return STUB;
    }
    return PROVIDER;
  }

  /** 能否证明有真实模型参与（只有 {@link #PROVIDER} 为真）。 */
  public boolean isProvider() {
    return this == PROVIDER;
  }
}
