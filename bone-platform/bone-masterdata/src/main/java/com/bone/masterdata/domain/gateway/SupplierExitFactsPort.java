package com.bone.masterdata.domain.gateway;

import com.bone.masterdata.domain.model.supplier.SupplierExitFacts;

/**
 * 供应商退出所需<b>上游三域事实</b>的入站端口（采购订单 / 合同 / 应付）。
 *
 * <p><b>为什么事实要过端口、不能由命令携带</b>（2026-11-16 独立审查 C-01 修复）：命令携带事实时，HTTP 调用方 只要在请求体里写「未结 PO =
 * 0」就能退出成功的供应商 —— 守卫变成「被守卫方自己声明合规」，spec 的「退出前置校验」 Scenario
 * 形同不存在。事实改由端口提供后，事实来源与调用方分离，负向注入只能发生在测试夹具里，不能发生在请求里。
 *
 * <p><b>本切片无三域上游</b>：生产实现属 R-2（须接 PO / 合同 / 应付），因此该端口在 main 里<b>没有</b>实现 —— 打开 {@code
 * bone.procurement.supplier-mdm.enabled} 而不提供实现会启动失败，这是刻意的 fail-closed：
 * 宁可起不来，也不允许在没有事实来源的情况下判定「可以退出」。
 */
public interface SupplierExitFactsPort {

  /** 查询该租户该主体的未结 PO / 合同 / 应付事实；查询不到应按「存在未结」处理，不得返回全零。 */
  SupplierExitFacts facts(long tenantId, String partyId);
}
