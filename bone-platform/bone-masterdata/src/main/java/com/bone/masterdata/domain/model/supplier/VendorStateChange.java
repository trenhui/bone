package com.bone.masterdata.domain.model.supplier;

/**
 * 一次 Vendor 状态迁移的审计记录。
 *
 * @param from 迁移前状态
 * @param event 触发事件
 * @param to 迁移后状态
 * @param source 命令来源（用于事后核对付款冻结是否由财务侧发起）
 * @param basis 守卫依据（原因码）
 * @param operator 操作人账号
 * @param at 迁移时间
 */
public record VendorStateChange(
    VendorState from,
    VendorEvent event,
    VendorState to,
    TransitionCommandSource source,
    String basis,
    String operator,
    java.time.Instant at) {}
