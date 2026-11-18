package com.bone.masterdata.domain.model.supplier;

/**
 * 迁移表中的一条 Vendor 迁移定义。
 *
 * @param from 起始状态
 * @param event 触发事件
 * @param to 目标状态
 * @param guarded 是否需要闸门/存量采购守卫结论
 */
public record VendorTransition(
    VendorState from, VendorEvent event, VendorState to, boolean guarded) {}
