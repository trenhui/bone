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

  /** 发票级免赔（GAP②）：在比例之前、年度免赔之前，对各费用池基数按比例分摊扣减；0 = 不启用。 */
  @Column(name = "invoice_deduct")
  private BigDecimal invoiceDeduct;

  /** 理算级免赔（GAP⑥）：在比例之后、控额之前，对理算金额再做一次绝对额扣减；0 = 不启用。 */
  @Column(name = "adjust_deduct")
  private BigDecimal adjustDeduct;

  /** 日赔付限额（Q-05）：同一业务日累计上限；0 = 不启用。 */
  @Column(name = "daily_limit")
  private BigDecimal dailyLimit;

  /** 日窗口口径（Q-05）：NONE / SAME_DAY_INVOICE（按同日发票）/ SAME_DAY_CLAIM（按同日赔案）。 */
  @Column(name = "daily_limit_basis")
  private String dailyLimitBasis;

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
    return of(
        id,
        tenantId,
        liabilityUuid,
        liabilityVersion,
        planId,
        liabilityCode,
        liabilityType,
        payPercent,
        liabilityDeduct,
        liabilityLimit,
        java.math.BigDecimal.ZERO,
        java.math.BigDecimal.ZERO,
        socialOnly,
        waitingPeriod,
        status);
  }

  /**
   * 含发票级 / 理算级免赔（GAP② / GAP⑥）的责任配置工厂。
   *
   * <p>两个新增免赔层默认 0（不启用），与未新增前行为完全一致；配置后由理算引擎在「比例前 / 比例后」 两个独立时点施加。
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
      java.math.BigDecimal invoiceDeduct,
      java.math.BigDecimal adjustDeduct,
      Integer socialOnly,
      Integer waitingPeriod,
      String status) {
    return of(
        id,
        tenantId,
        liabilityUuid,
        liabilityVersion,
        planId,
        liabilityCode,
        liabilityType,
        payPercent,
        liabilityDeduct,
        liabilityLimit,
        invoiceDeduct,
        adjustDeduct,
        java.math.BigDecimal.ZERO,
        "NONE",
        socialOnly,
        waitingPeriod,
        status);
  }

  /**
   * 含日限额（Q-05）的完整责任配置工厂。
   *
   * <p>日限额必须「金额 + 口径」一起给：只给金额无法判定"同日"是哪一天（发票日期 or 赔案受理日）， 装载期即被 {@code LiabilityRule#validate()}
   * 拒绝——这是 V1 §7.4 两档口径落地的配置入口。
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
      java.math.BigDecimal invoiceDeduct,
      java.math.BigDecimal adjustDeduct,
      java.math.BigDecimal dailyLimit,
      String dailyLimitBasis,
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
    liability.invoiceDeduct = invoiceDeduct == null ? java.math.BigDecimal.ZERO : invoiceDeduct;
    liability.adjustDeduct = adjustDeduct == null ? java.math.BigDecimal.ZERO : adjustDeduct;
    liability.dailyLimit = dailyLimit == null ? java.math.BigDecimal.ZERO : dailyLimit;
    liability.dailyLimitBasis =
        dailyLimitBasis == null || dailyLimitBasis.isBlank() ? "NONE" : dailyLimitBasis;
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

  /** 发票级免赔（GAP②）；0 或不配置表示不启用。 */
  public BigDecimal invoiceDeduct() {
    return invoiceDeduct == null ? BigDecimal.ZERO : invoiceDeduct;
  }

  /** 理算级免赔（GAP⑥）；0 或不配置表示不启用。 */
  public BigDecimal adjustDeduct() {
    return adjustDeduct == null ? BigDecimal.ZERO : adjustDeduct;
  }

  /** 日赔付限额（Q-05）；0 或不配置表示不启用。 */
  public BigDecimal dailyLimit() {
    return dailyLimit == null ? BigDecimal.ZERO : dailyLimit;
  }

  /** 日窗口口径（Q-05）；缺省 {@code NONE}（不启用）。 */
  public String dailyLimitBasis() {
    return dailyLimitBasis == null || dailyLimitBasis.isBlank() ? "NONE" : dailyLimitBasis;
  }
}
