package com.bone.claim.domain.model.liability;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;

/**
 * 责任费用类别规则（{@code t_adj_liability_fee_rule}，0094）。
 *
 * <p>在责任的统一比例/限额之上，按费用类别做差异化（如 手术费 100%、材料费 60% 且限 5000 元）。 挂 {@code
 * liabilityUuid}（配置态，各版本共享）而非版本键： 责任<b>发布</b>时由理算装载把生效规则快照进 {@code formula_snapshot}，历史案件按快照复算——
 * 表内规则随后修改只影响未来发布，与"已生效版本不可改"的版本化铁律不冲突。
 */
@Getter
@Table("t_adj_liability_fee_rule")
public class LiabilityFeeRule extends TenantAggregateRoot<Long> {

  @Column(name = "liability_uuid")
  private String liabilityUuid;

  /** 费用类别编码（须存在于 t_ref_fee_category，服务层校验）。 */
  @Column(name = "fee_code")
  private String feeCode;

  /** 费用类别名称（冗余，免联表展示）。 */
  @Column(name = "fee_name")
  private String feeName;

  /** 赔付比例（百分数 0-100）。 */
  @Column(name = "pay_percent")
  private BigDecimal payPercent;

  /** 该类别单次限额（0=不限）。 */
  @Column(name = "limit_amount")
  private BigDecimal limitAmount;

  /** 仅赔社保内（1是/0否）。 */
  @Column(name = "social_only")
  private Integer socialOnly;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version")
  private Long version;

  public static LiabilityFeeRule of(
      Long id,
      Long tenantId,
      String liabilityUuid,
      String feeCode,
      String feeName,
      BigDecimal payPercent,
      BigDecimal limitAmount,
      Integer socialOnly) {
    LiabilityFeeRule e = new LiabilityFeeRule();
    e.setId(id);
    e.setTenantId(tenantId);
    e.liabilityUuid = liabilityUuid;
    e.feeCode = feeCode == null ? "" : feeCode.trim();
    e.feeName = feeName == null ? "" : feeName.trim();
    e.payPercent = payPercent == null ? BigDecimal.ZERO : payPercent;
    e.limitAmount = limitAmount == null ? BigDecimal.ZERO : limitAmount;
    e.socialOnly = socialOnly == null ? 1 : socialOnly;
    Instant now = Instant.now();
    e.createdAt = now;
    e.updatedAt = now;
    return e;
  }
}
