package com.bone.claim.domain.model.claim.projection;

import java.math.BigDecimal;

/**
 * 已结案赔案的对账最小投影（§11.7 不变量 6 输入）。
 *
 * <p>为什么不直接用 {@code ClaimCase} 聚合：对账只需要「有没有结算单、应付多少」两个判断，加载整行聚合要连带 30 余列与乐观锁版本，
 * 而结案案件按日成批扫描时这些列一列都不用——读侧按投影取列是既有纪律（见 {@code ClaimHeadProjection}）。
 *
 * @param claimId 赔案 ID
 * @param claimNo 报案号（差异单的业务定位键）
 * @param approvedAmount 核定金额（仅在缺理算结果时作为「应付」兜底，<b>不作为</b>不变量 6 的判决依据，见 {@code
 *     DiffType#CASE_SETTLE_MISMATCH}）
 */
public record ClaimSettlementRef(Long claimId, String claimNo, BigDecimal approvedAmount) {}
