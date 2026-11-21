package com.bone.claim.domain.model.claim.projection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单案时长投影（时间窗内已结案案件的处理时长，毫秒）。
 *
 * <p>仓储只负责取出[{@code claimId}, {@code durationMs}]事实对，分位数（P50/P90）属统计口径，在应用层对 {@code durationMs}
 * 排序后取位——不放进 SQL，避免依赖数据库 PERCENTILE_CONT 方言。作为领域投影住在 domain 层，满足仓储返回类型白名单。
 *
 * <p><b>为什么是可变 POJO 而不是 record</b>：SDK 的 {@code SmartRowMapper} 先以无参构造建实例、再按列名反射写字段。record
 * 只有全参规范构造、 字段 final，真机实测抛 {@code Failed to instantiate} / {@code wrong number of arguments: 0
 * expected: 2}（与 ClaimWithItemsProjection 2026-10-25 联调同款）。字段名按 SDK camelCase↔snake_case 规则对齐列标签
 * {@code claim_id}/{@code duration_ms}。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaseDurationProjection {

  private Long claimId;
  private Long durationMs;
}
