package com.bone.claim.domain.model.liability;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import lombok.Getter;

/**
 * 责任配置保司覆盖项（{@code t_claim_coverage_override}，两段式模型 §policy-config-two-stage）。
 *
 * <p><b>一行一个维度</b>：主体 × 责任 × {@code override_field} 唯一——覆盖值变更走 UPDATE 而不是插新行，发布是行内 DISABLED→ACTIVE
 * 的状态迁移，停用即回落标准。回放靠 {@code standard_value} 快照（留痕），合并永远取实时标准（见 {@link
 * CoverageOverrideApplicationService#preview}）。
 *
 * <p><b>维度词汇</b>：{@code LIMIT_HOSPITAL_CODES} / {@code LIMIT_DIAGNOSIS_CODES} （名单分隔串）与 {@code
 * FEE_RULE.&lt;feeCode&gt;}（JSON：payPercent/limitAmount/socialOnly）。
 * 为什么不建独立子表：名单维度天然是「整列替换」，费用规则维度已有独立表承载标准侧， 覆盖侧再镜像一张表只会得到两份需要同步的结构——JSON 值 + 单表足够，且发布/回滚语义简单。
 */
@Getter
@Table("t_claim_coverage_override")
public class ClaimCoverageOverride extends TenantAggregateRoot<Long> {

  public static final String STATUS_DISABLED = "DISABLED";
  public static final String STATUS_ACTIVE = "ACTIVE";

  public static final String FIELD_LIMIT_HOSPITAL_CODES = "LIMIT_HOSPITAL_CODES";
  public static final String FIELD_LIMIT_DIAGNOSIS_CODES = "LIMIT_DIAGNOSIS_CODES";
  public static final String FEE_RULE_PREFIX = "FEE_RULE.";

  @Column(name = "biz_identity_code")
  private String bizIdentityCode;

  /** 责任 UUID（配置态，各版本共享；与 LiabilityFeeRule.liabilityUuid 同口径）。 */
  @Column(name = "coverage_uuid")
  private String coverageUuid;

  @Column(name = "override_field")
  private String overrideField;

  /** 创建/发布时的标准值快照（留痕；合并永远取实时标准）。 */
  @Column(name = "standard_value")
  private String standardValue;

  @Column(name = "override_value")
  private String overrideValue;

  @Column(name = "status")
  private String status = STATUS_DISABLED;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version")
  private Long version;

  public static ClaimCoverageOverride of(
      Long id,
      Long tenantId,
      String bizIdentityCode,
      String coverageUuid,
      String overrideField,
      String standardValue,
      String overrideValue) {
    if (bizIdentityCode == null || bizIdentityCode.isBlank()) {
      throw new IllegalArgumentException("覆盖项必须挂业务主体");
    }
    if (coverageUuid == null || coverageUuid.isBlank()) {
      throw new IllegalArgumentException("覆盖项必须挂责任");
    }
    if (overrideField == null || overrideField.isBlank()) {
      throw new IllegalArgumentException("覆盖维度不能为空");
    }
    ClaimCoverageOverride e = new ClaimCoverageOverride();
    e.setId(id);
    e.setTenantId(tenantId);
    e.bizIdentityCode = bizIdentityCode.trim();
    e.coverageUuid = coverageUuid.trim();
    e.overrideField = overrideField.trim();
    e.standardValue = standardValue == null ? "" : standardValue;
    e.overrideValue = overrideValue == null ? "" : overrideValue;
    e.status = STATUS_DISABLED;
    Instant now = Instant.now();
    e.createdAt = now;
    e.updatedAt = now;
    return e;
  }

  /** 覆盖值编辑：仅未发布行可改（已发布行先停用——否则"预览确认的"和"实际生效的"会分叉）。 */
  public void updateValue(String overrideValue, Instant now) {
    if (STATUS_ACTIVE.equals(this.status)) {
      throw new IllegalStateException("已发布的覆盖项不可直接编辑，请先停用");
    }
    this.overrideValue = overrideValue == null ? "" : overrideValue;
    this.updatedAt = now;
  }

  /** 刷新标准值快照（发布/同步时调用；不触碰 override_value 与 status）。 */
  public void refreshStandardSnapshot(String standardValue, Instant now) {
    this.standardValue = standardValue == null ? "" : standardValue;
    this.updatedAt = now;
  }

  public void publish(Instant now) {
    this.status = STATUS_ACTIVE;
    this.updatedAt = now;
  }

  public void disable(Instant now) {
    this.status = STATUS_DISABLED;
    this.updatedAt = now;
  }

  public boolean isActive() {
    return STATUS_ACTIVE.equals(this.status);
  }
}
