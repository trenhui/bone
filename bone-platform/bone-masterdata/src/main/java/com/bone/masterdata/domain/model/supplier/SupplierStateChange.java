package com.bone.masterdata.domain.model.supplier;

/**
 * 一次 Supplier 状态迁移的审计记录（spec Scenario「合法准入路径」要求记录迁移时间、操作人与依据）。
 *
 * @param from 迁移前状态
 * @param event 触发事件
 * @param to 迁移后状态
 * @param basis 守卫依据（原因码）；无守卫迁移为 null
 * @param operator 操作人账号
 * @param at 迁移时间
 */
public record SupplierStateChange(
    SupplierState from,
    SupplierEvent event,
    SupplierState to,
    String basis,
    String operator,
    java.time.Instant at) {}
