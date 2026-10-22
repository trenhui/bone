package com.bone.claim.domain.model.liability;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * 保险责任配置（A4 读侧实体，`t_adj_liability`）。
 *
 * <p>本域不负责责任配置的 CRUD —— 配置属运营资产，由迁移/运营导入（§8.3 配置外部化铁律： 规则与责任参数一律 DB JSON 承载、经 `bone-ai-rules`
 * 热更，改配置不发车）。理赔域只做 **读取 + 版本锁定**：理算时把当期配置连同 `liabilityVersion` 一起钉入 {@code formula_snapshot}
 * （§5.2「物化不可仅版本引用」），保证历史案件可自包含复算。
 *
 * <p>一期落地字段取 §5.2 实证模型的最小可用集：赔付比例 / 免赔 / 责任额度 / 限定。
 */
@Getter
@Table("t_adj_liability")
public class AdjLiability extends TenantAggregateRoot<Long> {

  /** 责任 UUID（版本化主键：同一 uuid 多版本并存，已生效版本不可改）。 */
  @Column(name = "liability_uuid")
  private String liabilityUuid;

  /** 责任版本。 */
  @Column(name = "liability_version")
  private String liabilityVersion;

  @Column(name = "plan_id")
  private Long planId;

  /** 责任编码（R01…）。 */
  @Column(name = "liability_code")
  private String liabilityCode;

  /** 责任类型：REIMBURSEMENT 报销 / FIXED_AMOUNT 定额 / ALLOWANCE 津贴。 */
  @Column(name = "liability_type")
  private String liabilityType;

  /** 赔付比例（百分数，如 90 表示 90%；社保内外/医院等级差异化由 factor 表达）。 */
  @Column(name = "pay_percent")
  private BigDecimal payPercent;

  /** 年度免赔额（R-DED-03：仅累计社保范围内个人自付部分）。 */
  @Column(name = "liability_deduct")
  private BigDecimal liabilityDeduct;

  /** 责任额度上限（R-CAP；与维度可用额取 min 后截断）。 */
  @Column(name = "liability_limit")
  private BigDecimal liabilityLimit;

  /** 是否仅赔付社保范围内（false 则目录外也按比例赔）。 */
  @Column(name = "social_only")
  private Integer socialOnly;

  /** 等待期（天，§5.2.1 ⑤：以出险日 − 保单生效日计算）。 */
  @Column(name = "waiting_period")
  private Integer waitingPeriod;

  /** 状态：DRAFT/GRAY/ACTIVE/RETIRED。 */
  @Column(name = "status")
  private String status;

  /**
   * 责任配置工厂（运营/迁移导入入口）。
   *
   * <p>不提供 {@code setXxx}：配置版本化后已生效版本不可改（§4.1），没有 setter 即无从改起—— 变更走「新增版本」而非 update。
   */
  public static AdjLiability of(
      Long id,
      Long tenantId,
      String liabilityUuid,
      String liabilityVersion,
      Long planId,
      String liabilityCode,
      String liabilityType,
      java.math.BigDecimal payPercent,
      java.math.BigDecimal liabilityDeduct,
      java.math.BigDecimal liabilityLimit,
      Integer socialOnly,
      Integer waitingPeriod,
      String status) {
    AdjLiability liability = new AdjLiability();
    liability.setId(id);
    liability.setTenantId(tenantId);
    liability.liabilityUuid = liabilityUuid;
    liability.liabilityVersion = liabilityVersion;
    liability.planId = planId;
    liability.liabilityCode = liabilityCode == null ? "" : liabilityCode;
    liability.liabilityType = liabilityType == null ? "REIMBURSEMENT" : liabilityType;
    liability.payPercent = payPercent == null ? java.math.BigDecimal.ZERO : payPercent;
    liability.liabilityDeduct =
        liabilityDeduct == null ? java.math.BigDecimal.ZERO : liabilityDeduct;
    liability.liabilityLimit = liabilityLimit == null ? java.math.BigDecimal.ZERO : liabilityLimit;
    liability.socialOnly = socialOnly == null ? 1 : socialOnly;
    liability.waitingPeriod = waitingPeriod == null ? 0 : waitingPeriod;
    liability.status = status == null ? "DRAFT" : status;
    return liability;
  }

  public boolean isActive() {
    return "ACTIVE".equals(status);
  }

  /** 仅赔社保内（默认 true，R-DIR-01）。 */
  public boolean isSocialOnly() {
    return socialOnly == null || socialOnly == 1;
  }

  public int waitingPeriodDays() {
    return waitingPeriod == null ? 0 : waitingPeriod;
  }

  /** 赔付比例（0–1，缺省 0，避免"默认 90%"的隐形口径，§8.2 R-RAT）。 */
  public BigDecimal payRate() {
    return payPercent == null
        ? BigDecimal.ZERO
        : payPercent.divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP);
  }

  public BigDecimal deduct() {
    return liabilityDeduct == null ? BigDecimal.ZERO : liabilityDeduct;
  }

  public BigDecimal limit() {
    return liabilityLimit == null ? BigDecimal.ZERO : liabilityLimit;
  }
}
