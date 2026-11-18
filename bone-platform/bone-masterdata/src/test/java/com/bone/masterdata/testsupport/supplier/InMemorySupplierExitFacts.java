package com.bone.masterdata.testsupport.supplier;

import com.bone.masterdata.domain.gateway.SupplierExitFactsPort;
import com.bone.masterdata.domain.model.supplier.SupplierExitFacts;
import java.math.BigDecimal;

/**
 * 上游三域事实的联调夹具（未结 PO / 合同 / 应付）。
 *
 * <p>默认「上游已结清」以便正向路径直接可跑；负向用例显式注入未结事实（{@link #withOpenPurchaseOrders(int)} 等）， 从而让「有未结 PO 就退出必红」这条
 * Scenario 真的具备否决能力。
 */
public final class InMemorySupplierExitFacts implements SupplierExitFactsPort {

  // 可空：null 表示「上游未作答」，用于验证退出校验在故障态 fail-closed（独立审查 IMPORTANT-4）。
  private Integer openPurchaseOrders = 0;
  private Integer openContracts = 0;
  private BigDecimal payableBalance = BigDecimal.ZERO;

  @Override
  public SupplierExitFacts facts(long tenantId, String partyId) {
    return new SupplierExitFacts(openPurchaseOrders, openContracts, payableBalance);
  }

  public InMemorySupplierExitFacts withOpenPurchaseOrders(int count) {
    this.openPurchaseOrders = count;
    return this;
  }

  /** 模拟上游未作答 / 查询失败：事实为 null 而不是 0。 */
  public InMemorySupplierExitFacts withUnknownPurchaseOrders() {
    this.openPurchaseOrders = null;
    return this;
  }

  public InMemorySupplierExitFacts withOpenContracts(int count) {
    this.openContracts = count;
    return this;
  }

  /** 模拟上游未作答 / 查询失败：事实为 null 而不是 0。 */
  public InMemorySupplierExitFacts withUnknownContracts() {
    this.openContracts = null;
    return this;
  }

  public InMemorySupplierExitFacts withPayableBalance(BigDecimal balance) {
    this.payableBalance = balance;
    return this;
  }

  public void reset() {
    openPurchaseOrders = 0;
    openContracts = 0;
    payableBalance = BigDecimal.ZERO;
  }
}
