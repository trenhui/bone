package com.bone.masterdata.domain.gateway;

import com.bone.masterdata.domain.model.supplier.GuardVerdict;
import com.bone.masterdata.domain.model.supplier.VendorActivationContext;
import com.bone.masterdata.domain.model.supplier.VendorDeactivationContext;

/**
 * Vendor 交易付款资格的闸门守卫端口（设计 D6）。
 *
 * <p>两个方法对应两条不同的跨域事实集：开通闸门（准入/银行/税务/付款条件/重复发票）与停用前置（进行中采购）。合并成一个「万能
 * canChange」会让守卫无法表达「各自需要什么事实」，也会让缺失事实被静默当成通过。
 *
 * <p>与 {@link SupplierExitGuard} 同样返回「布尔结论 + 原因码」，不返回硬编码 true。
 */
public interface VendorActivationGuard {

  /** 开通闸门：五项全通过才可开通。 */
  GuardVerdict canActivate(VendorActivationContext context);

  /** 停用前置：不得留下进行中的采购。 */
  GuardVerdict canDeactivate(VendorDeactivationContext context);
}
