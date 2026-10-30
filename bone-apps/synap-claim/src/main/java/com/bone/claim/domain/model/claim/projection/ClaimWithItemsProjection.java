package com.bone.claim.domain.model.claim.projection;

import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 赔案 + 明细扁平投影（SQL 每明细一行）。
 *
 * <p>{@code findById} 不回填聚合内集合（SDK 语义），明细一律经本投影获取——与 blueprint Order 同款纪律。
 *
 * <p><b>为什么是可变 POJO 而不是 record</b>：SDK 的行映射（{@code SmartRowMapper}）先调 {@code
 * BeanUtils.instantiateClass(Class)} 建实例、再按列名反射写字段，即<strong>必须有无参构造且字段可写</strong>。 record
 * 只有全参规范构造、字段 final，实测直接抛 {@code Failed to instantiate} / {@code wrong number of arguments: 0
 * expected: 37}（2026-10-25 联调）。字段名按 SDK 的 camelCase→snake_case 规则与列标签对齐（{@code claimId}↔ {@code
 * claim_id}、{@code itemValidAmount}↔{@code item_valid_amount}），无需逐个写 {@code @Column}。
 *
 * <p>{@code @AllArgsConstructor} 仅服务单测夹具的整行构造；运行时由 SDK 走无参构造。{@code externalStatus} 不是数据库列，故不设字段，由
 * {@link ClaimStatus#toExternal()} 派生。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClaimWithItemsProjection {

  private Long claimId;
  private String claimNo;
  private String policyNo;
  private Long planId;
  private String insuredIdHash;
  private String claimType;
  private String status;
  private String stage;
  private int hangUpStatus;
  private String invoiceCode;
  private String invoiceNo;
  private java.time.Instant outInsureTime;
  private String sourceChannel;
  private String sourceSystem;
  private BigDecimal claimAmount;
  private BigDecimal approvedAmount;
  private String firstReviewerId;
  private String secondReviewerId;
  private String rejectComment;
  private String rejectType;
  private String planVersion;
  private java.time.Instant createdAt;

  // ---- 明细列（无明细行为 null）----
  private Long itemId;
  private String itemName;
  private String itemCode;
  private String itemCategory;
  private BigDecimal itemQuantity;
  private BigDecimal itemUnitPrice;
  private BigDecimal itemAmount;
  private String itemSocialFlag;
  private String itemMedicalType;
  private BigDecimal itemPoolingAmount;
  private BigDecimal itemSelfPay1;
  private BigDecimal itemSelfPay2;
  private BigDecimal itemSelfPayC;

  /** 第三方已赔付（社保统筹之外：商保/公账/其他基金）。理算的"不可获利"护栏依赖此列。 */
  private BigDecimal itemThirdPartyPaid;

  private BigDecimal itemValidAmount;
  private BigDecimal itemInvalidAmount;

  // ---- 「次」分组维度（V1 §7.3；发票级三列一案一值，科室为行级）----
  /**
   * 发票开票日期（<b>发票级</b>：一赔案一发票，故案内恒定）。
   *
   * <p>历史案件为 {@code null}——不得用 {@code outInsureTime} 顶替：出险日与开票日可以不同， 顶替会让"同日"分组按错误日期切分，且错误不可见。
   */
  private java.time.LocalDate invoiceDate;

  /** 医院编码（发票级；按编码而非名称比对，名称可改、编码是身份）。 */
  private String hospitalCode;

  /** 病种/诊断编码（发票级；一次就诊一个主要诊断）。 */
  private String diagnosisCode;

  /** 就诊科室（<b>行级</b>：一张发票可跨科室，这是"同院同科室"档唯一能拆分的维度）。 */
  private String itemDepartment;

  /** 对外状态码（pk-tpa 口径）；{@code status} 非枚举名时返回 null，不抛异常污染读链路。 */
  @JsonProperty("externalStatus")
  public String externalStatus() {
    if (status == null) {
      return null;
    }
    try {
      return ClaimStatus.valueOf(status).toExternal();
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
