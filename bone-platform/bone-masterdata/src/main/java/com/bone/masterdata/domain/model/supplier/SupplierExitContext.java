package com.bone.masterdata.domain.model.supplier;

import java.math.BigDecimal;

/**
 * 供应商退出前置事实（跨域：采购订单 / 合同 / 应付 + 本域 Vendor 角色）。
 *
 * <p><b>为什么是事实容器而不是判断</b>：前三项分别属于 PO、合同、应付三个上游域，采购主数据域无权查询，只负责把「退出需要哪些条件」 声明清楚；第四项（{@code
 * openVendorCount}）是本域事实，由应用层点算 Vendor 仓库后填入。跨域三项一律经 {@code SupplierExitFactsPort}
 * 获取，<b>不接受调用方自报</b> —— 让被守卫方自己声明「已结清」等于把守卫做成恒真断言（第一轮审查 C-01）。接上游后由 adapter 查询填充，领域语义零变化（设计 D6）。
 *
 * @param openPurchaseOrders 未结采购订单数；<b>null 表示未知</b>，按「存在未结」处理
 * @param openContracts 未结合同数；<b>null 表示未知</b>，按「存在未结」处理
 * @param payableBalance 未清应付余额；<b>null 表示未知</b>，按「存在未结」处理
 * @param openVendorCount 仍未停用的 Vendor 角色数（本域点算）
 */
public record SupplierExitContext(
    long tenantId,
    String partyId,
    Integer openPurchaseOrders,
    Integer openContracts,
    BigDecimal payableBalance,
    int openVendorCount) {

  /**
   * 应付已结清：余额必须<b>已知且为零</b>。
   *
   * <p>{@code payableBalance} 为 null 时判为「未结清」：null 是「应付域没给出答案」（未接入 / 查询失败），不是「没有欠款」。
   * 把未知当零会让上游不可用的环境静默通过退出校验，因此这里是 fail-closed（独立审查 I-1）。
   *
   * <p><b>为什么单独抽成谓词</b>：{@link #cleared()} 与守卫实现的拒绝分支都要判这一条。若各写一遍， 两处只要有一处被改成
   * null-tolerant，退出校验就会一半 fail-open 一半 fail-closed（探针实测过这种分叉）。
   */
  public boolean payableSettled() {
    return payableBalance != null && payableBalance.signum() == 0;
  }

  /** 采购订单已结清：数量必须<b>已知且为零</b>（null = 上游未作答，按存在未结处理）。 */
  public boolean purchaseOrdersSettled() {
    return openPurchaseOrders != null && openPurchaseOrders == 0;
  }

  /** 合同已结清：数量必须<b>已知且为零</b>（null = 上游未作答，按存在未结处理）。 */
  public boolean contractsSettled() {
    return openContracts != null && openContracts == 0;
  }

  /** 四项事实全部成立才算可退出；任一事实未知都判为未清（fail-closed）。 */
  public boolean cleared() {
    return purchaseOrdersSettled()
        && contractsSettled()
        && payableSettled()
        && openVendorCount == 0;
  }
}
