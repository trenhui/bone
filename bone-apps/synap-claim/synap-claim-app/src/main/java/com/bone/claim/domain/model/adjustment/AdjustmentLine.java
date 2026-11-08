package com.bone.claim.domain.model.adjustment;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * 理算明细行（`t_adj_calculation_line`）。
 *
 * <p>六个归因成分金额（§7.4）是拒赔五要素「事实认定」的数据基础——只给一个结果金额无法回答 "这个数字怎么来的"，也无法支撑申诉举证与后督复盘： {@code
 * feeCalculate}（计算基准） → {@code feeDeduct}（免赔扣减） → {@code ratioPayout}（按比例应付） → {@code
 * feeOccupied}（占额） → {@code feePayout}（实付） → {@code feeRemain}（剩余可用额度）。
 */
@Getter
@Table("t_adj_calculation_line")
public class AdjustmentLine extends AbstractEntity {

  /** 租户（列 NOT NULL）：明细行随理算聚合落库，从聚合根继承租户口径，缺列即 1364。 */
  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  /** 仅聚合根同包赋值（Adjustment.apply）。 */
  void setTenantId(Long tenantId) {
    this.tenantId = tenantId;
  }

  @Column(name = "calculation_id")
  private Long calculationId;

  @Column(name = "liability_uuid")
  private String liabilityUuid;

  /** 行类型：INPUT 输入 / RULE 规则处理 / OUTPUT 输出。 */
  @Column(name = "line_type")
  private String lineType;

  /** 输入快照（明细金额与三段拆分，JSON）。 */
  @Column(name = "input_snapshot")
  private String inputSnapshot;

  /** 计算基准（合理费用合计，社保外已按 socialOnly 剔除）。 */
  @Column(name = "fee_calculate")
  private BigDecimal feeCalculate;

  /** 免赔扣减额（R-DED-03：仅社保范围内自付一参与累计）。 */
  @Column(name = "fee_deduct")
  private BigDecimal feeDeduct;

  /** 按比例应付额。 */
  @Column(name = "ratio_payout")
  private BigDecimal ratioPayout;

  /** 占额（本行占用责任额度）。 */
  @Column(name = "fee_occupied")
  private BigDecimal feeOccupied;

  /** 实付额。 */
  @Column(name = "fee_payout")
  private BigDecimal feePayout;

  /** 剩余可用额度。 */
  @Column(name = "fee_remain")
  private BigDecimal feeRemain;

  /** 零赔付归因（受控枚举，禁自由文本）。 */
  @Column(name = "zero_reason")
  private String zeroReason;

  /** 拒赔/零赔付原因码（§8.3 受控字典 01-13 + 20/30/40）。 */
  @Column(name = "reject_type")
  private String rejectType;

  /** 逐行公式哈希（复算校验）。 */
  @Column(name = "formula_hash")
  private String formulaHash;

  AdjustmentLine() {}

  AdjustmentLine(
      Long id,
      Long calculationId,
      String liabilityUuid,
      String lineType,
      String inputSnapshot,
      BigDecimal feeCalculate,
      BigDecimal feeDeduct,
      BigDecimal ratioPayout,
      BigDecimal feeOccupied,
      BigDecimal feePayout,
      BigDecimal feeRemain,
      String zeroReason,
      String rejectType,
      String formulaHash) {
    setId(id);
    this.calculationId = calculationId;
    this.liabilityUuid = liabilityUuid;
    this.lineType = lineType;
    this.inputSnapshot = inputSnapshot;
    this.feeCalculate = feeCalculate;
    this.feeDeduct = feeDeduct;
    this.ratioPayout = ratioPayout;
    this.feeOccupied = feeOccupied;
    this.feePayout = feePayout;
    this.feeRemain = feeRemain;
    this.zeroReason = zeroReason;
    this.rejectType = rejectType;
    this.formulaHash = formulaHash;
    // 审计列由基类映射但**不会自动填充**：DDL 两列 NOT NULL 无默认值，缺赋值即 1364（批量插入整体失败）
    java.util.Date now = new java.util.Date();
    setCreatedAt(now);
    setUpdatedAt(now);
  }

  static AdjustmentLine of(
      Long id,
      Long calculationId,
      String liabilityUuid,
      String lineType,
      String inputSnapshot,
      BigDecimal feeCalculate,
      BigDecimal feeDeduct,
      BigDecimal ratioPayout,
      BigDecimal feeOccupied,
      BigDecimal feePayout,
      BigDecimal feeRemain,
      String zeroReason,
      String rejectType,
      String formulaHash) {
    return new AdjustmentLine(
        id,
        calculationId,
        liabilityUuid,
        lineType,
        inputSnapshot,
        feeCalculate,
        feeDeduct,
        ratioPayout,
        feeOccupied,
        feePayout,
        feeRemain,
        zeroReason,
        rejectType,
        formulaHash);
  }
}
