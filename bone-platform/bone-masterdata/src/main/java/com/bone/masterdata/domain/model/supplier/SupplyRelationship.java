package com.bone.masterdata.domain.model.supplier;

import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 供货关系聚合根（Purchasing Org data，Owner = 采购）。
 *
 * <p><b>为什么是独立聚合</b>：`采购领域架构设计方案.md:105,109` 裁决供货关系为聚合根 —— 一条供货关系对应一个采购组织下的商务条件， 与法人的合作资格（{@link
 * Supplier}）、财务侧付款资格（{@link Vendor}）各自独立演进。三者只以 partyId 关联。
 *
 * <p><b>不可变语义</b>：{@link #addItem} 与 {@link #reprice} 返回新实例，不修改既有实例与其条目；这保证并发读到的历史快照不会被追溯性改写。
 */
public final class SupplyRelationship {

  private final long tenantId;
  private final String relationshipId;
  private final String partyId;
  private final String purchasingOrgId;
  private final List<SupplierItem> items;

  private SupplyRelationship(
      long tenantId,
      String relationshipId,
      String partyId,
      String purchasingOrgId,
      List<SupplierItem> items) {
    this.tenantId = tenantId;
    this.relationshipId = relationshipId;
    this.partyId = partyId;
    this.purchasingOrgId = purchasingOrgId;
    this.items = items;
  }

  /** 建立一条供货关系；partyId 与采购组织必填。 */
  public static SupplyRelationship open(
      long tenantId, String relationshipId, String partyId, String purchasingOrgId) {
    if (isBlank(partyId)) {
      throw new DomainException("供货关系缺少 partyId，禁止构造");
    }
    if (isBlank(purchasingOrgId)) {
      throw new DomainException("供货关系缺少采购组织，禁止构造");
    }
    if (isBlank(relationshipId)) {
      throw new DomainException("供货关系缺少 relationshipId，禁止构造");
    }
    return new SupplyRelationship(
        tenantId, relationshipId.trim(), partyId.trim(), purchasingOrgId.trim(), List.of());
  }

  /** 追加一个供货条目；同一 itemCode 的同一版本重复登记直接拒绝。 */
  public SupplyRelationship addItem(SupplierItem item) {
    if (item == null) {
      throw new DomainException("待追加的供货条目为空");
    }
    boolean duplicated =
        items.stream()
            .anyMatch(
                existing ->
                    existing.itemCode().equals(item.itemCode())
                        && existing.version() == item.version());
    if (duplicated) {
      throw new DomainException("供货条目已存在同版本记录，禁止覆盖: " + item.itemCode() + " v" + item.version());
    }
    List<SupplierItem> next = new ArrayList<>(items);
    next.add(item);
    return new SupplyRelationship(
        tenantId, relationshipId, partyId, purchasingOrgId, List.copyOf(next));
  }

  /** 改价：为既有条目追加版本 +1 的新条目，不修改原条目。 */
  public SupplyRelationship reprice(String itemCode, BigDecimal newUnitPrice, String uom) {
    SupplierItem current =
        currentItem(itemCode).orElseThrow(() -> new DomainException("供货条目不存在，无法改价: " + itemCode));
    return addItem(SupplierItem.of(itemCode, current.version() + 1L, newUnitPrice, uom));
  }

  /** 取某条目的当前生效版本（版本号最大者）；不存在返回 empty。 */
  public Optional<SupplierItem> currentItem(String itemCode) {
    if (isBlank(itemCode)) {
      return Optional.empty();
    }
    return items.stream()
        .filter(item -> item.itemCode().equals(itemCode.trim()))
        .max(Comparator.comparingLong(SupplierItem::version));
  }

  private static boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }

  public long tenantId() {
    return tenantId;
  }

  public String relationshipId() {
    return relationshipId;
  }

  public String partyId() {
    return partyId;
  }

  public String purchasingOrgId() {
    return purchasingOrgId;
  }

  public List<SupplierItem> items() {
    return items;
  }
}
