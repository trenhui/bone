package com.bone.claim.infrastructure.fraud;

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
  public FraudResult screen(ClaimContextPort.ClaimContextView ctx) {
    return new FraudResult(false, null, false);
  }
}
