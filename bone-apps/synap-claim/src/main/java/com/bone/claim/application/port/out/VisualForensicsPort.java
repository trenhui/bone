package com.bone.claim.application.port.out;

import java.math.BigDecimal;

/**
 * 视觉反欺诈端口（出站，对接 BONE-AI 增强能力 E1：影像指纹/篡改检测/跨案重复检索）。
 *
 * <p>一期本地桩返回"未篡改、相似度风险 0"；二期接私有化视觉模型（权重禁云端）。 机制进平台、阈值与业务判定（同发票跨案）留产品（R-FRAUD-08）。
 */
public interface VisualForensicsPort {

  ForensicsResult inspect(ClaimContextPort.ClaimContextView ctx);

  record ForensicsResult(boolean tampered, BigDecimal similarityRisk) {}
}
