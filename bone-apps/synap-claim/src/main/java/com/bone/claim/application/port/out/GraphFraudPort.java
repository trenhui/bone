package com.bone.claim.application.port.out;

/**
 * 图计算反欺诈端口（出站，对接 BONE-AI 增强能力 E2：关系图谱/设备指纹/团伙识别）。
 *
 * <p>一期本地桩返回"未命中"且 {@code available=false}；二期接国产图库（Nebula/TuGraph）做多跳关联与社区发现（R-FRAUD-09/10/11）。 图谱
 * schema 与团伙判定策略留产品，机制进平台。
 *
 * <p>安全契约：{@link FraudResult#available()} 表示"本次是否真实执行了图计算反欺诈筛查"。 能力未集成（本地桩）时必须为 {@code
 * false}——<b>不可用绝不等同于无团伙</b>， 决策图据此 fail-closed 升级人工，而非默认放行（R-FRAUD 硬闸不得静默通过，§6.3）。
 */
public interface GraphFraudPort {

  FraudResult screen(ClaimContextPort.ClaimContextView ctx);

  /**
   * @param hit 是否命中风险团伙（仅当 {@code available=true} 时可信）
   * @param clusterId 命中团伙/社区标识（仅当 {@code available=true} 时非空）
   * @param available 本次是否真实执行了图计算反欺诈筛查（本地桩=false；E2 接入后=true）
   */
  record FraudResult(boolean hit, String clusterId, boolean available) {}
}
