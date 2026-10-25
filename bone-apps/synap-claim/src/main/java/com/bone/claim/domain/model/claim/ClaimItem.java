package com.bone.claim.domain.model.claim;

import com.bone.claim.domain.model.claim.valueobject.ItemCategory;
import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 费用明细项（ClaimCase 聚合内实体）。
 *
 * <p>医保三段拆分字段（{@code poolingAmount}/{@code selfPay1}/{@code selfPay2}/{@code selfPayC}）是 R-DED-03
 * 免赔口径（仅累计自付一）与 R-RAT-01 赔付比例的唯一数据基础（工程落地版 §9.3）。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("t_claim_item")
public class ClaimItem extends TenantAbstractEntity<Long> {

  /** 所属案件（级联外键，与根同事务落盘）。 */
  @Column(name = "claim_id", nullable = false)
  private Long claimId;

  @Column(name = "item_name", nullable = false)
  private String itemName;

  /** 医保项目/药品编码（目录编码匹配的唯一键，禁名称匹配，§5.2.1）。 */
  @Column(name = "item_code")
  private String itemCode;

  /** 费用类别（DB 存枚举 name）。 */
  @Column(name = "category", nullable = false)
  private String category;

  @Column(name = "quantity", nullable = false)
  private BigDecimal quantity;

  @Column(name = "unit_price", nullable = false)
  private BigDecimal unitPrice;

  @Column(name = "amount", nullable = false)
  private BigDecimal amount;

  /** 社保内外（IN/OUT/SELF）。 */
  @Column(name = "social_flag")
  private String socialFlag = "";

  /** 医保费用类型（甲类/乙类/丙类）。 */
  @Column(name = "medical_type")
  private String medicalType = "";

  @Column(name = "pooling_amount", nullable = false)
  private BigDecimal poolingAmount = BigDecimal.ZERO;

  @Column(name = "self_pay_1", nullable = false)
  private BigDecimal selfPay1 = BigDecimal.ZERO;

  @Column(name = "self_pay_2", nullable = false)
  private BigDecimal selfPay2 = BigDecimal.ZERO;

  @Column(name = "self_pay_c", nullable = false)
  private BigDecimal selfPayC = BigDecimal.ZERO;

  @Column(name = "third_party_paid", nullable = false)
  private BigDecimal thirdPartyPaid = BigDecimal.ZERO;

  /** 合理费用（理算基准）。 */
  @Column(name = "valid_amount", nullable = false)
  private BigDecimal validAmount = BigDecimal.ZERO;

  /** 不合理费用。 */
  @Column(name = "invalid_amount", nullable = false)
  private BigDecimal invalidAmount = BigDecimal.ZERO;

  /** 费用类别枚举。 */
  public ItemCategory categoryEnum() {
    return category == null ? ItemCategory.SERVICE : ItemCategory.ofName(category);
  }

  public static ClaimItem of(
      long id,
      Long tenantId,
      Long claimId,
      String itemName,
      String itemCode,
      ItemCategory category,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal amount) {
    if (itemName == null || itemName.isBlank()) {
      throw new IllegalArgumentException("明细项名称不能为空");
    }
    if (amount == null || amount.signum() < 0) {
      throw new IllegalArgumentException("明细金额不能为负");
    }
    ClaimItem item = new ClaimItem();
    item.setId(id);
    item.setTenantId(tenantId);
    item.claimId = claimId;
    item.itemName = itemName;
    item.itemCode = itemCode == null ? "" : itemCode;
    item.category = (category == null ? ItemCategory.SERVICE : category).name();
    item.quantity = quantity == null ? BigDecimal.ONE : quantity;
    item.unitPrice = unitPrice == null ? BigDecimal.ZERO : unitPrice;
    item.amount = amount;
    // ⚠ 必须显式设置审计时间：SDK 的级联（@Cascade）插入不会自动填充 created_at/updated_at
    // （基类只有字段声明，无自动填充钩子），而 MySQL 侧 created_at/updated_at 是 NOT NULL——
    // 不设置会抛 "Column 'created_at' cannot be null"，且异常发生在根 save 之后。
    java.util.Date now = new java.util.Date();
    item.setCreatedAt(now);
    item.setUpdatedAt(now);
    return item;
  }
}
