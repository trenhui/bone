package com.bone.claim.application.port.out;

import java.math.BigDecimal;

/**
 * 视觉反欺诈端口（出站，对接 BONE-AI 增强能力 E1：影像指纹/篡改检测/跨案重复检索）。
 *
 * <p>一期本地桩返回"未篡改、相似度风险 0"且 {@code available=false}； 二期接私有化视觉模型（权重禁云端）。
 * 机制进平台、阈值与业务判定（同发票跨案）留产品（R-FRAUD-08）。
 *
 * <p>安全契约：{@link ForensicsResult#available()} 表示"本次是否真实执行了视觉反欺诈筛查"。 能力未集成（本地桩）时必须为 {@code
 * false}——<b>不可用绝不等同于无篡改</b>， 决策图据此 fail-closed 升级人工，而非默认放行（R-FRAUD 硬闸不得静默通过，§6.3）。
 */
public interface VisualForensicsPort {

  ForensicsResult inspect(ClaimContextPort.ClaimContextView ctx);

  /**
   * @param tampered 是否检出篡改（仅当 {@code available=true} 时可信）
   * @param similarityRisk 跨案重复/相似度风险（0~1，仅当 {@code available=true} 时可信）
   * @param available 本次是否真实执行了视觉反欺诈筛查（本地桩=false；E1 接入后=true）
   */
  record ForensicsResult(boolean tampered, BigDecimal similarityRisk, boolean available) {}
}
