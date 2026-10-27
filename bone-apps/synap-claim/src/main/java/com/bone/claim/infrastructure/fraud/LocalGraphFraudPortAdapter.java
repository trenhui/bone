package com.bone.claim.infrastructure.fraud;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.GraphFraudPort;
import org.springframework.stereotype.Component;

/**
 * 图计算反欺诈适配器（一期本地桩：默认未命中）。
 *
 * <p>二期接 BONE-AI 增强能力 E2（关系图谱/设备指纹/团伙识别，国产图库 Nebula/TuGraph）， 图谱 schema
 * 与团伙判定策略留产品（R-FRAUD-09/10/11）。
 */
@Component
public class LocalGraphFraudPortAdapter implements GraphFraudPort {

  @Override
  public FraudResult screen(ClaimContextPort.ClaimContextView ctx) {
    return new FraudResult(false, null);
  }
}
