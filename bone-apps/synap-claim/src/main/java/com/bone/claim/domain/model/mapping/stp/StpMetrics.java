package com.bone.claim.domain.model.mapping.stp;

/**
 * STP 闸门输入指标（按 保司×域 采集一次）。
 *
 * <p><b>代理口径说明（诚实标注，非精确流量口径）</b>：本仓尚未建「请求级解析审计日志」，命中率/直通率以 {@code t_claim_mapping_miss}
 * 为数据源做有界代理，足以驱动闸门且不引入新表：
 *
 * <ul>
 *   <li>{@code itemHitRate}：resolved / (resolved + open)——同一 (保司,域) 下历史未命中队列的解决占比；
 *   <li>{@code caseStraightThroughRate}：曾命中未命中的案件中、当前未卡在 OPEN/reopen 的占比（按 {@code last_case_no}
 *       去重）；
 *   <li>{@code openMissCount} / {@code correctionEvents7d} / {@code
 *       conflictCount}：硬条件，直接从队列表/映射表聚合。
 * </ul>
 *
 * 精确流量口径（每请求解析成功/失败）需新增审计日志表，列为后续增强（见待拍板清单）。
 */
public record StpMetrics(
    long tenantId,
    String insurerCode,
    String domain,
    /** 明细级命中率（%，代理：resolved/(resolved+open)）。 */
    double itemHitRate,
    /** 赔案级直通率（%，代理：未卡在未命中队列的涉案占比）。 */
    double caseStraightThroughRate,
    /** OPEN 未命中数（硬条件）。 */
    long openMissCount,
    /** 近 7 天纠错（reopen）事件数（硬条件）。 */
    long correctionEvents7d,
    /** 冲突数：同保司码出现 >1 个标准码候选（硬条件）。 */
    long conflictCount) {}
