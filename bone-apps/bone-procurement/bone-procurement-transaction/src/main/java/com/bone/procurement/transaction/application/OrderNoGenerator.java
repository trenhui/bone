package com.bone.procurement.transaction.application;

import com.bone.procurement.transaction.domain.repository.PurchaseOrderRepository;
import com.bone.procurement.transaction.domain.repository.RequisitionRepository;
import java.time.Year;
import java.util.function.IntSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 单号生成器：{@code PR-{yyyy}-{6位}} / {@code PO-{yyyy}-{6位}}。
 *
 * <p>按「唯一约束兜底 + 冲突重试 ≤3 次」；年份由 IntSupplier 提供以便测试注入。
 */
@Component
public class OrderNoGenerator {

  private static final int MAX_RETRY = 3;

  private final RequisitionRepository requisitionRepository;
  private final PurchaseOrderRepository purchaseOrderRepository;
  private final IntSupplier year;

  /** 生产装配：当前年份。显式标注注入点，避免两个构造器并存时 Spring 退回寻找无参构造。 */
  @Autowired
  public OrderNoGenerator(
      RequisitionRepository requisitionRepository,
      PurchaseOrderRepository purchaseOrderRepository) {
    this(requisitionRepository, purchaseOrderRepository, () -> Year.now().getValue());
  }

  /** 测试装配：固定年份。 */
  public OrderNoGenerator(
      RequisitionRepository reqRepo, PurchaseOrderRepository poRepo, IntSupplier year) {
    this.requisitionRepository = reqRepo;
    this.purchaseOrderRepository = poRepo;
    this.year = year;
  }

  public String nextPoRequisitionNo(Long tenantId) {
    return next(tenantId, "PR", year.getAsInt(), requisitionRepository::hasNo);
  }

  public String nextPurchaseOrderNo(Long tenantId) {
    return next(tenantId, "PO", year.getAsInt(), purchaseOrderRepository::hasNo);
  }

  private String next(Long tenantId, String prefix, int y, ExistsCheck exists) {
    for (int seq = 1; seq <= MAX_RETRY; seq++) {
      String no = prefix + "-" + y + "-" + String.format("%06d", seq);
      if (!exists.check(tenantId, no)) {
        return no;
      }
    }
    throw new IllegalStateException("单号生成重试 " + MAX_RETRY + " 次后仍冲突：" + prefix + "-" + y);
  }

  @FunctionalInterface
  private interface ExistsCheck {
    boolean check(Long tenantId, String no);
  }
}
