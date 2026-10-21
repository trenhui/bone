package com.bone.ailedger.domain.model;

import java.math.BigDecimal;

/**
 * 维度范围的额度快照（对账 / 监控 / 运营视图）。
 *
 * <p>恒等式：{@code available = limit - consumed - reserved - frozen}。四个分量分别对应「已实际占用 / 已预留未提交 /
 * 已冻结」——直付场景里「冻结」用于先占住资金待外部确认（借鉴 pk-tpa-saas 直付冻结/解冻），与「预留」的区别是冻结无预留单、 直接扣减可用额。
 *
 * @param scope 维度范围
 * @param limit 登记上限（未登记为 0）
 * @param consumed 已实际占用
 * @param reserved 已预留未提交
 * @param frozen 已冻结
 * @param available 当前可用
 */
public record LimitSnapshot(
    LimitScope scope,
    BigDecimal limit,
    BigDecimal consumed,
    BigDecimal reserved,
    BigDecimal frozen,
    BigDecimal available) {}
