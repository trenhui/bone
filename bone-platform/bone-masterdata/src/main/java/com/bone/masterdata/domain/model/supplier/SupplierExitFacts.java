package com.bone.masterdata.domain.model.supplier;

import java.math.BigDecimal;

/**
 * 供应商退出的<b>上游跨域事实</b>（采购订单 / 合同 / 应付）。
 *
 * <p><b>为什么单独成 record 而不复用 {@link SupplierExitContext}</b>：退出裁决需要「上游三域事实 + Vendor 角色关闭状态」两类输入， 前者属于
 * PO / 合同 / 应付三个上游域，只能由事实端口提供；后者属于本域，由应用层直接查 Vendor 仓库得到。 把两类输入分开表达，可以让「哪些事实是外部给的、哪些是本域算的」在类型上就看得见
 * —— 混在一个对象里最容易出现 「把外部事实当成本域结论」或反之的自证。
 *
 * <p><b>端口来源</b>：{@link com.bone.masterdata.domain.gateway.SupplierExitFactsPort}。实现方（未来）负责查询三域；
 * 本切片由测试夹具提供，生产上下文缺该 bean 时能力开关打开即启动失败（fail-closed）。
 *
 * <p><b>三项事实都允许 {@code null}，且 {@code null} 一律表示「上游未作答 / 查询失败」，不得当成零</b>（独立审查 IMPORTANT-4）： 用
 * {@code int} 表达时，「上游未接入」只能传 0，等价于告诉守卫「已结清」—— 故障态直接把退出校验变成 fail-open。
 *
 * @param openPurchaseOrders 未结采购订单数；null 表示未知，按「存在未结」处理
 * @param openContracts 未结合同数；null 表示未知，按「存在未结」处理
 * @param payableBalance 未清应付余额；null 表示未知，按「存在未结」处理
 */
public record SupplierExitFacts(
    Integer openPurchaseOrders, Integer openContracts, BigDecimal payableBalance) {

  public SupplierExitFacts {
    if ((openPurchaseOrders != null && openPurchaseOrders < 0)
        || (openContracts != null && openContracts < 0)) {
      throw new IllegalArgumentException("未结数量不得为负");
    }
  }
}
