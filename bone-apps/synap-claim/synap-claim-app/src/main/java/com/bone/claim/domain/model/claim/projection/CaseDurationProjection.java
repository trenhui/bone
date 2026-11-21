package com.bone.claim.domain.model.claim.projection;

/**
 * 单案时长投影（时间窗内已结案案件的处理时长，毫秒）。
 *
 * <p>仓储只负责取出[{@code claimId}, {@code durationMs}]事实对，分位数（P50/P90）属统计口径， 在应用层对 {@code durationMs}
 * 排序后取位——不放进 SQL，避免依赖数据库 PERCENTILE_CONT 方言。 作为领域投影住在 domain 层，满足仓储返回类型白名单（List<领域投影> 合规）。
 */
public record CaseDurationProjection(Long claimId, Long durationMs) {}
