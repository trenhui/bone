package com.bone.masterdata.domain.gateway;

import com.bone.masterdata.domain.model.supplier.GuardVerdict;
import com.bone.masterdata.domain.model.supplier.SupplierExitContext;

/**
 * 供应商退出的跨域守卫端口（设计 D6）。
 *
 * <p><b>刻意不返回硬编码 true</b>：守卫恒真会让 spec「退出前置校验」Scenario 变成假绿 —— 无论上游有多少未结 PO，测试都会通过。 本端口返回「布尔结论 +
 * 原因码」，既能在本切片用夹具注入「存在未结 PO → 退出必红」，也能在上游域就绪时替换实现而领域语义零变化。
 *
 * <p>实现方（未来）负责把 PO / 合同 / 应付三个上游事实聚合成结论：事实本身经 {@link SupplierExitFactsPort} 取得、{@code
 * openVendorCount} 由应用层点算，二者一并装进 {@link SupplierExitContext} 再交给本端口裁决 —— 事实来源与裁决逻辑分离，
 * 调用方既不能自报事实，也不能绕开裁决。
 */
public interface SupplierExitGuard {

  GuardVerdict canExit(SupplierExitContext context);
}
