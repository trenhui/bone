package com.bone.claim.domain.model.config;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import java.util.Date;
import lombok.Getter;

/**
 * 费用类型字典（§9.5 A4，§8.3 三段拆分与费用类型子限额的承载，可变）。
 *
 * <p><b>用途</b>：理算引擎按「费用类型 × 费用类别」拆分理算单元——类别比例覆盖责任统一比例、 类别限额控额前截断、社保内外取严。本字典定义费用类型（录入项 INPUT / 计算项
 * CALCULATE）、父子层级与默认适用。
 *
 * <p><b>可变表</b>：带 {@code @Version} 乐观锁；审计列由 {@link TenantAbstractEntity} 注入， 工厂显式赋值 {@code
 * created_at}/{@code updated_at}（NOT NULL 无默认）。
 */
@Getter
@Table("t_adj_fee_type")
public class AdjFeeType extends TenantAbstractEntity<Long> {

  /** 来源：录入项。 */
  public static final String SOURCE_INPUT = "INPUT";

  /** 来源：计算项。 */
  public static final String SOURCE_CALCULATE = "CALCULATE";

  /** 状态：生效。 */
  public static final String STATUS_ACTIVE = "ACTIVE";

  /** 状态：停用。 */
  public static final String STATUS_RETIRED = "RETIRED";

  @Column(name = "fee_type_code", nullable = false)
  private String feeTypeCode;

  @Column(name = "fee_type_name", nullable = false)
  private String feeTypeName;

  @Column(name = "source", nullable = false)
  private String source;

  @Column(name = "input_code")
  private String inputCode;

  @Column(name = "calculate_formula")
  private String calculateFormula;

  @Column(name = "parent_code")
  private String parentCode;

  @Column(name = "is_default")
  private Integer isDefault;

  @Column(name = "sort_no")
  private Integer sortNo;

  @Column(name = "status")
  private String status;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  AdjFeeType() {}

  public static AdjFeeType create(
      Long tenantId,
      String feeTypeCode,
      String feeTypeName,
      String source,
      String inputCode,
      String calculateFormula,
      String parentCode,
      Integer isDefault,
      Integer sortNo,
      String status,
      Instant now) {
    AdjFeeType feeType = new AdjFeeType();
    feeType.setTenantId(tenantId);
    feeType.feeTypeCode = feeTypeCode == null ? "" : feeTypeCode;
    feeType.feeTypeName = feeTypeName == null ? "" : feeTypeName;
    feeType.source = source == null ? SOURCE_INPUT : source;
    feeType.inputCode = inputCode == null ? "" : inputCode;
    feeType.calculateFormula = calculateFormula == null ? "" : calculateFormula;
    feeType.parentCode = parentCode == null ? "" : parentCode;
    feeType.isDefault = isDefault == null ? 0 : isDefault;
    feeType.sortNo = sortNo == null ? 0 : sortNo;
    feeType.status = status == null ? STATUS_ACTIVE : status;
    feeType.version = 0L;
    Date nowDate = Date.from(now);
    feeType.setCreatedAt(nowDate);
    feeType.setUpdatedAt(nowDate);
    return feeType;
  }
}
