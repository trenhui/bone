package com.bone.claim.infrastructure.fraud;

import com.bone.claim.application.port.out.AiCapabilityDeclaration;
import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.GraphFraudPort;
import org.springframework.stereotype.Component;

/**
 * 图计算反欺诈适配器（一期本地桩：未执行真实筛查，{@code available=false}）。
 *
 * <p>关键：桩返回 {@code available=false}（而非假装"已筛查无团伙"），决策图据此 fail-closed 升级人工； 绝不用 {@code hit=false}
 * 伪装无风险。二期接 BONE-AI 增强能力 E2（关系图谱/设备指纹/团伙识别，国产图库 Nebula/TuGraph）， 届时 {@code available=true} 并回填真实
 * {@code hit}/{@code clusterId}（R-FRAUD-09/10/11）。
 */
@Component
public class LocalGraphFraudPortAdapter implements GraphFraudPort {

  @Override
  public AiCapabilityDeclaration declaration() {
    return AiCapabilityDeclaration.stub(
        "E2_GRAPH", "ANTI_FRAUD_E2_NOT_INTEGRATED", "接入国产图库做多跳关联/社区发现并同步覆盖本声明");
  }

  @Override
  public FraudResult screen(ClaimContextPort.ClaimContextView ctx) {
    return new FraudResult(false, null, false);
  }
}
