package com.bone.claim.infrastructure.ai;

import com.bone.claim.application.port.out.AiGatewayPort;
import com.bone.claim.application.port.out.AiGatewayUnavailableException;
import com.bone.claim.application.port.out.ClaimContextPort;
import java.math.BigDecimal;
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
  public AiAssessment assess(ClaimContextPort.ClaimContextView ctx) {
    if (!enabled) {
      throw new AiGatewayUnavailableException("AI 网关不可用（本地桩关闭）");
    }
    return new AiAssessment(
        true, false, AI_CONFIDENCE, List.of(), LOCAL_MODEL, LOCAL_PROMPT_VERSION);
  }
}
