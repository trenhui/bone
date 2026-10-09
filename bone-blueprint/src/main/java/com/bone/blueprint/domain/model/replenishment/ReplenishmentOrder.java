package com.bone.blueprint.domain.model.replenishment;

import com.bone.blueprint.domain.model.replenishment.valueobject.ReplenishmentStatus;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 补货单聚合根 —— 供应链「低于安全库存 → 补货 → 到货入库」的凭证。
 *
 * <p><b>与 Inventory 的边界</b>：Inventory 回答「现在有多少」；本聚合回答「计划补多少、谁批了、何时到货」。 到货入库由应用层调用 {@code
 * Inventory.receive}，不在此聚合内改库存数量（跨聚合不直写）。
 *
 * <p><b>事件豁免（E-5.4）</b>：状态迁移无跨聚合订阅方；入库由应用层编排库存，故不发 DomainEvent。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("bp_replenishment_order")
public class ReplenishmentOrder extends TenantAggregateRoot<Long> {

  private static final DateTimeFormatter NO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

  private String replenishNo;
  private Long productId;
  private String productName;
  private String warehouseCode;
  private Integer quantity;
  private Integer suggestedQty;
  private Integer availableSnapshot;
  private Integer safetySnapshot;
  private String supplierCode;
  private ReplenishmentStatus status;
  private String remark;
  private Instant submittedAt;
  private Instant approvedAt;
  private Instant receivedAt;
  private Instant createdAt;
  private Instant updatedAt;

  @Version private Long version;

  private ReplenishmentOrder(
      long id,
      Long tenantId,
      Long productId,
      String productName,
      String warehouseCode,
      int quantity,
      Integer suggestedQty,
      Integer availableSnapshot,
      Integer safetySnapshot,
      String supplierCode,
      String remark) {
    setId(id);
    setTenantId(tenantId);
    this.productId = productId;
    this.productName = productName;
    this.warehouseCode =
        warehouseCode == null || warehouseCode.isBlank() ? "DEFAULT" : warehouseCode.trim();
    if (quantity <= 0) {
      throw new IllegalArgumentException("补货数量必须为正: " + quantity);
    }
    this.quantity = quantity;
    this.suggestedQty = suggestedQty;
    this.availableSnapshot = availableSnapshot;
    this.safetySnapshot = safetySnapshot;
    this.supplierCode = supplierCode;
    this.remark = remark;
    this.status = ReplenishmentStatus.DRAFT;
    this.replenishNo = generateNo(id);
    touch();
  }

  public static ReplenishmentOrder create(
      long id,
      Long tenantId,
      Long productId,
      String productName,
      String warehouseCode,
      int quantity,
      Integer suggestedQty,
      Integer availableSnapshot,
      Integer safetySnapshot,
      String supplierCode,
      String remark) {
    return new ReplenishmentOrder(
        id,
        tenantId,
        productId,
        productName,
        warehouseCode,
        quantity,
        suggestedQty,
        availableSnapshot,
        safetySnapshot,
        supplierCode,
        remark);
  }

  /** 建议补货量：补到「安全库存 × 2」水位（至少 1）。 */
  public static int suggestQuantity(int availableQty, int safetyStock) {
    int target = Math.max(safetyStock, 0) * 2;
    return Math.max(target - Math.max(availableQty, 0), 1);
  }

  public void updatePlan(int quantity, String supplierCode, String remark) {
    touch();
    if (!status.canSubmit()) {
      throw new DomainException("仅草稿可改计划: " + status + "（" + replenishNo + "）");
    }
    if (quantity <= 0) {
      throw new IllegalArgumentException("补货数量必须为正: " + quantity);
    }
    this.quantity = quantity;
    this.supplierCode = supplierCode;
    this.remark = remark;
  }

  public void submit(Instant at) {
    touch();
    if (!status.canSubmit()) {
      throw new DomainException("当前状态不可提交: " + status + "（" + replenishNo + "）");
    }
    this.status = ReplenishmentStatus.SUBMITTED;
    this.submittedAt = at;
  }

  public void approve(Instant at) {
    touch();
    if (!status.canApprove()) {
      throw new DomainException("当前状态不可审批: " + status + "（" + replenishNo + "）");
    }
    this.status = ReplenishmentStatus.APPROVED;
    this.approvedAt = at;
  }

  public void markReceived(Instant at) {
    touch();
    if (!status.canReceive()) {
      throw new DomainException("当前状态不可入库: " + status + "（" + replenishNo + "）");
    }
    this.status = ReplenishmentStatus.RECEIVED;
    this.receivedAt = at;
  }

  public void cancel() {
    touch();
    if (!status.canCancel()) {
      throw new DomainException("当前状态不可取消: " + status + "（" + replenishNo + "）");
    }
    this.status = ReplenishmentStatus.CANCELLED;
  }

  private static String generateNo(long id) {
    return "RP" + LocalDate.now().format(NO_FORMATTER) + id;
  }

  private void touch() {
    this.updatedAt = Instant.now();
    if (this.createdAt == null) {
      this.createdAt = this.updatedAt;
    }
  }
}
