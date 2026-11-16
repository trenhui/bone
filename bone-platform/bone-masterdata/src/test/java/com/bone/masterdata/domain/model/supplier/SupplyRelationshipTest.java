package com.bone.masterdata.domain.model.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.DomainException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 供货关系（Purchasing Org data，Owner = 采购）的关系约束与不可变语义。
 *
 * <p>依据 `采购领域架构设计方案.md:109`：`SupplyRelationship` / `SupplierItem` 承载采购侧 Purchasing Org data， 与财务侧的
 * Vendor（Company Code data）分属不同 Owner。因此本聚合独立于 {@link Supplier} 与 {@link Vendor} 的生命周期状态机，只以
 * partyId 关联。
 */
@DisplayName("供货关系：关系约束与供货条目不可变语义")
class SupplyRelationshipTest {

  private static final long TENANT = 7L;

  @Test
  @DisplayName("关系必须绑定已有 partyId 与采购组织，缺失即拒绝")
  void requiresPartyIdAndPurchasingOrg() {
    assertThatThrownBy(() -> SupplyRelationship.open(TENANT, "REL-1", " ", "PO-ORG-1"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("partyId");

    assertThatThrownBy(() -> SupplyRelationship.open(TENANT, "REL-1", "PARTY-1", ""))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("采购组织");
  }

  @Test
  @DisplayName("同一采购组织下重复 itemCode 的同一版本被拒绝")
  void rejectsDuplicateItemCodeAtSameVersion() {
    SupplyRelationship relationship =
        SupplyRelationship.open(TENANT, "REL-1", "PARTY-1", "PO-ORG-1");
    SupplyRelationship withItem =
        relationship.addItem(SupplierItem.of("ITEM-1", 1L, new BigDecimal("12.50"), "EA"));

    assertThatThrownBy(
            () -> withItem.addItem(SupplierItem.of("ITEM-1", 1L, new BigDecimal("99.00"), "EA")))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("ITEM-1");
  }

  @Test
  @DisplayName("改价产出新版本条目，原条目保持不变")
  void repriceAppendsNewVersionWithoutMutatingOriginal() {
    SupplyRelationship relationship =
        SupplyRelationship.open(TENANT, "REL-1", "PARTY-1", "PO-ORG-1")
            .addItem(SupplierItem.of("ITEM-1", 1L, new BigDecimal("12.50"), "EA"));

    SupplierItem original = relationship.currentItem("ITEM-1").orElseThrow();
    SupplyRelationship repriced = relationship.reprice("ITEM-1", new BigDecimal("13.80"), "EA");
    SupplierItem latest = repriced.currentItem("ITEM-1").orElseThrow();

    assertThat(latest.version()).isEqualTo(2L);
    assertThat(latest.unitPrice()).isEqualByComparingTo("13.80");
    assertThat(original.version()).isEqualTo(1L);
    assertThat(original.unitPrice()).isEqualByComparingTo("12.50");
    assertThat(relationship.items()).hasSize(1);
    assertThat(repriced.items()).hasSize(2);
  }

  @Test
  @DisplayName("对不存在的条目改价被拒绝")
  void rejectsRepriceOfUnknownItem() {
    SupplyRelationship relationship =
        SupplyRelationship.open(TENANT, "REL-1", "PARTY-1", "PO-ORG-1");

    assertThatThrownBy(() -> relationship.reprice("ITEM-X", new BigDecimal("1.00"), "EA"))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("ITEM-X");
  }
}
