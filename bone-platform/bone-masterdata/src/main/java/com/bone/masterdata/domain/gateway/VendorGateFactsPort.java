package com.bone.masterdata.domain.gateway;

import com.bone.masterdata.domain.model.supplier.VendorActivationContext;
import com.bone.masterdata.domain.model.supplier.VendorDeactivationContext;

/**
 * Vendor 闸门所需要素<b>财务域事实</b>的入站端口（开通五项 / 停用两项）。
 *
 * <p><b>为什么五项不能由命令携带</b>（2026-11-16 独立审查 C-01 修复）：准入是否通过、银行是否核验、税务是否核验、
 * 付款条件是否约定、是否无重复发票风险，全部是财务侧的核验结论。允许 HTTP 调用方在请求体里声明「五项全 true」， 等于「未经闸门不得开通」（spec:77-79）这条 Scenario
 * 从来不可能失败 —— 闸门只剩形式。
 *
 * <p>本切片未接财务域，main 里没有实现；打开能力开关而不提供该 bean 会启动失败（fail-closed）。生产实现属 R-2。
 */
public interface VendorGateFactsPort {

  /** 开通闸门五项事实；任一查询失败按「未通过」处理，不得默认为 true。 */
  VendorActivationContext activationContext(long tenantId, String partyId);

  /** 停用前置两项事实（进行中 PO 数、存量 PO 是否允许走完）。 */
  VendorDeactivationContext deactivationContext(long tenantId, String partyId);
}
