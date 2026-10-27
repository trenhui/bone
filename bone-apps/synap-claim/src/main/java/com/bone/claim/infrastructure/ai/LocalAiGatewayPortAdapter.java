package com.bone.claim.infrastructure.ai;

import com.bone.claim.application.port.out.AiGatewayPort;
import com.bone.claim.application.port.out.AiGatewayUnavailableException;
import com.bone.claim.application.port.out.ClaimContextPort;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * AI 评估网关适配器（一期本地确定性桩）。
 *
 * <p>默认可用：返回置信度 0.93、不建议拒赔；{@code setEnabled(false)} 模拟网关故障， 使 {@link #assess} 抛 {@link
 * AiGatewayUnavailableException} 触发决策图 LLM 节点降级（FALLBACK）。 二期替换为 bone-ai C5
 * 网关（模型档位/配额/降档/调用审计/成本红线）+ C6 受控 RAG。
 */
@Component
public class LocalAiGatewayPortAdapter implements AiGatewayPort {

  private static final BigDecimal AI_CONFIDENCE = new BigDecimal("0.93");

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
    return new AiAssessment(true, false, AI_CONFIDENCE);
  }
}
