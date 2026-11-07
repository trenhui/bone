package com.bone.aidoc.domain.model;

/**
 * 单一风险等级的标定结论（ADR-0046 §四：阈值须按风险等级分别标定）。
 *
 * <p><b>统计纪律</b>：{@code observedFalseAcceptRate} 只是点估计；只有 Wilson 95% 置信上界不超过目标 （{@code
 * statisticallyProven}）才允许把 {@code suggestedThreshold} 作为生产阈值。样本量为 0 或 「零错误但样本很少」都不得宣称达标——ADR
 * 明确「小样本零错误不可宣称达标」。
 *
 * @param suggestedThreshold 建议阈值（未通过统计证明时仅为开发自测候选，禁止上生产）
 * @param totalSamples 该等级样本总数
 * @param autoPassed 在建议阈值下被自动通过的样本数
 * @param falseAccepts 其中识别错误的样本数（错误放行）
 * @param observedFalseAcceptRate 观测错误放行率（autoPassed=0 时为 0）
 * @param wilsonUpperBound 错误放行率的 Wilson 95% 上界（autoPassed=0 时为 1.0）
 * @param statisticallyProven Wilson 上界是否不超过目标
 * @param minSamplesRequired 「零错误」情形下证明该目标所需的最小自动通过样本数
 * @param noSafeThreshold 所有候选阈值都含错误放行样本（应强制人工复核 / 加强 L2 规则）
 */
public record LevelCalibration(
    RiskLevel level,
    RiskThreshold suggestedThreshold,
    int totalSamples,
    int autoPassed,
    int falseAccepts,
    double observedFalseAcceptRate,
    double wilsonUpperBound,
    boolean statisticallyProven,
    int minSamplesRequired,
    boolean noSafeThreshold) {}
