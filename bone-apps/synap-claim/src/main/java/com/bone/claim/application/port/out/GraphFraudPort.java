package com.bone.claim.application.port.out;

/**
 * 图计算反欺诈端口（出站，对接 BONE-AI 增强能力 E2：关系图谱/设备指纹/团伙识别）。
 *
 * <p>一期本地桩返回"未命中"；二期接国产图库（Nebula/TuGraph）做多跳关联与社区发现（R-FRAUD-09/10/11）。 图谱 schema 与团伙判定策略留产品，机制进平台。
 */
public interface GraphFraudPort {

  FraudResult screen(ClaimContextPort.ClaimContextView ctx);

  record FraudResult(boolean hit, String clusterId) {}
}
