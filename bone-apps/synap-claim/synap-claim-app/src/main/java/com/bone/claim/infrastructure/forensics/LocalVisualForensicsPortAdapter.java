package com.bone.claim.infrastructure.forensics;

import com.bone.claim.application.port.out.ClaimContextPort;
import com.bone.claim.application.port.out.VisualForensicsPort;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * 视觉反欺诈适配器（一期本地桩：未执行真实筛查，{@code available=false}）。
 *
 * <p>关键：桩返回 {@code available=false}（而非假装"已核验无篡改"），决策图据此 fail-closed 升级人工； 绝不用 {@code
 * tampered=false} 伪装无风险。二期接 BONE-AI 增强能力 E1（影像指纹/篡改检测/跨案重复检索）， 权重私有化（禁云端），届时 {@code available=true}
 * 并回填真实 {@code tampered}/{@code similarityRisk}。
 */
@Component
public class LocalVisualForensicsPortAdapter implements VisualForensicsPort {

  @Override
  public ForensicsResult inspect(ClaimContextPort.ClaimContextView ctx) {
    return new ForensicsResult(false, BigDecimal.ZERO, false);
  }
}
