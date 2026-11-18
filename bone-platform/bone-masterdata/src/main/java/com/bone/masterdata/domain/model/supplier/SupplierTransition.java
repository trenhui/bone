package com.bone.masterdata.domain.model.supplier;

/**
 * 迁移表中的一条迁移定义。
 *
 * @param from 起始状态
 * @param event 触发事件
 * @param to 目标状态
 * @param guarded 是否需要跨域守卫结论（需要时调用方必须提供 {@link GuardVerdict}，缺失即拒绝）
 */
public record SupplierTransition(
    SupplierState from, SupplierEvent event, SupplierState to, boolean guarded) {}
