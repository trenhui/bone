package com.bone.claim.infrastructure.forensics;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.VisualForensicsPort;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 视觉反欺诈适配器（一期本地桩：默认未篡改、相似度风险 0）。
 *
 * <p>二期接 BONE-AI 增强能力 E1（影像指纹/篡改检测/跨案重复检索），权重私有化（禁云端）。
 */
@Component
public class LocalVisualForensicsPortAdapter implements VisualForensicsPort {

  @Override
  public ForensicsResult inspect(ClaimContextPort.ClaimContextView ctx) {
    return new ForensicsResult(false, BigDecimal.ZERO);
  }
}
