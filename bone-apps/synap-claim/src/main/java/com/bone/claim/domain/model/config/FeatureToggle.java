package com.bone.claim.domain.model.config;

import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import java.util.Date;
import lombok.Getter;

/**
 * 租户级特性开关与灰度（§11.4 三态开关）。
 *
 * <p><b>用途</b>：承载新能力的渐进发布与安全灰度（如 {@code AUTO_APPROVE} 自动放款按租户开启 / 按比例灰度）。
 * 未配置某特性的租户视为「继承全局开关」（fail-closed：默认不开启自动放款等资金策略，避免能力未就绪即放量）。
 *
 * <p><b>三态语义</b>：{@code ENABLED} 全量开启 / {@code DISABLED} 显式关闭 / 无记录＝继承全局默认。 {@code rollout_pct}
 * 为灰度比例（0–100），接入方按 {@code hash(tenantId) % 100 < rollout_pct} 判定是否命中灰度。
 */
@Getter
@Table("t_feature_toggle")
public class FeatureToggle extends TenantAbstractEntity<Long> {

  /** 自动放款特性键（§11.4 / AutoApprovalApplicationService）。 */
  public static final String FEATURE_AUTO_APPROVE = "AUTO_APPROVE";

  public static final String STATE_ENABLED = "ENABLED";
  public static final String STATE_DISABLED = "DISABLED";

  @Column(name = "feature_key", nullable = false)
  private String featureKey;

  @Column(name = "state", nullable = false)
  private String state;

  @Column(name = "rollout_pct", nullable = false)
  private Integer rolloutPct;

  @Column(name = "remark")
  private String remark;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  FeatureToggle() {}

  public static FeatureToggle create(
      Long tenantId, String featureKey, String state, int rolloutPct, String remark, Instant now) {
    FeatureToggle toggle = new FeatureToggle();
    toggle.setTenantId(tenantId);
    toggle.featureKey = featureKey;
    toggle.state = state == null ? STATE_DISABLED : state;
    toggle.rolloutPct = rolloutPct;
    toggle.remark = remark == null ? "" : remark;
    toggle.version = 0L;
    // 审计列由 SDK 映射但**不会自动填充**：DDL 两列 NOT NULL 无默认值，必须显式赋值
    Date nowDate = Date.from(now);
    toggle.setCreatedAt(nowDate);
    toggle.setUpdatedAt(nowDate);
    return toggle;
  }

  /** 是否处于开启态（ENABLED 且灰度命中）。 */
  public boolean isOn(long tenantId) {
    if (!STATE_ENABLED.equals(state)) {
      return false;
    }
    if (rolloutPct == null || rolloutPct >= 100) {
      return true;
    }
    if (rolloutPct <= 0) {
      return false;
    }
    return Math.floorMod(tenantId, 100) < rolloutPct;
  }
}
