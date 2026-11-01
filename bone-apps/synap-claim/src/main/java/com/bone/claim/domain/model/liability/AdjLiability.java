package com.bone.claim.domain.model.liability;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
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

  /** 所属保障/险种 ID（四级建模：保单→方案→保障→责任）。 */
  @Column(name = "coverage_id")
  private Long coverageId;

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

  /** 适用限定方式（V1 §2 要素 6）：NONE / WHITELIST / BLACKLIST。 */
  @Column(name = "limit_mode")
  private String limitMode;

  /** 适用限定——医院编码名单（分隔串；空 = 该维度不限定）。 */
  @Column(name = "limit_hospital_codes")
  private String limitHospitalCodes;

  /** 适用限定——病种编码名单（分隔串；空 = 该维度不限定）。 */
  @Column(name = "limit_diagnosis_codes")
  private String limitDiagnosisCodes;

  /**
   * 目录准入策略（缺口 ① / 适用限定「药品·诊疗」维度）：{@code NONE} 不查目录 / {@code MANDATORY} 必须查到结论。
   *
   * <p>{@code NONE} 时行为与引入本机制之前逐字一致；{@code MANDATORY} 时每条明细都要有目录结论， 缺结论即退补件（不猜）。与 {@link
   * #catalogCode} 必须同配或同不配，否则装载期拒绝。
   */
  @Column(name = "catalog_policy")
  private String catalogPolicy;

  /** 目录编码（如 {@code NATIONAL_2024}）：回答"按哪一版目录判"。 */
  @Column(name = "catalog_code")
  private String catalogCode;

  /** 是否仅赔付社保范围内（false 则目录外也按比例赔）。 */
  @Column(name = "social_only")
  private Integer socialOnly;

  /** 等待期（天，§5.2.1 ⑤：以出险日 − 保单生效日计算）。 */
  @Column(name = "waiting_period")
  private Integer waitingPeriod;

  /** 状态：DRAFT/GRAY/ACTIVE/RETIRED。 */
  @Column(name = "status")
  private String status;

  /** 审计时间（框架注入列 NOT NULL 无默认值，必须在工厂里显式赋值，见 ClaimPlan 同约定）。 */
  @Column(name = "created_at", nullable = false)
  private java.time.Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private java.time.Instant updatedAt;

  /** SDK 乐观锁版本（并发编辑/发布/下线冲突检测，领域层不手写）。所有兄弟聚合均有此列。 */
  @Version private Long version;

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
   * 含日限额（Q-05）的责任配置工厂（无保障归属，兼容历史数据，{@code coverageId} 置 null）。
   *
   * <p>本重载不接收 {@code coverageId}，统一按 null 归并到最终构造器， 责任不强制归属某个保障（历史未分级数据允许挂在计划级）。
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
    return of(
        id,
        tenantId,
        liabilityUuid,
        liabilityVersion,
        planId,
        null,
        liabilityCode,
        liabilityType,
        payPercent,
        liabilityDeduct,
        liabilityLimit,
        invoiceDeduct,
        adjustDeduct,
        dailyLimit,
        dailyLimitBasis,
        socialOnly,
        waitingPeriod,
        status);
  }

  /**
   * 含保障关联（四级建模：保单→方案→保障→责任）的<b>最终落地构造器</b>。
   *
   * <p>其余 {@code of(...)} 重载（含/不含日限额、限定、目录）都把参数收敛到此方法； 责任归属到某个 {@code coverageId}（保障/险种），缺失时置
   * null（DDL 列 {@code coverage_id DEFAULT NULL}）， 表示该责任挂在计划级（兼容历史未分级数据）。
   */
  public static AdjLiability of(
      Long id,
      Long tenantId,
      String liabilityUuid,
      String liabilityVersion,
      Long planId,
      Long coverageId,
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
    liability.coverageId = coverageId;
    liability.liabilityCode = liabilityCode == null ? "" : liabilityCode;
    liability.liabilityType = liabilityType == null ? "REIMBURSEMENT" : liabilityType;
    liability.payPercent = payPercent == null ? BigDecimal.ZERO : payPercent;
    liability.liabilityDeduct =
        liabilityDeduct == null ? java.math.BigDecimal.ZERO : liabilityDeduct;
    liability.liabilityLimit = liabilityLimit == null ? java.math.BigDecimal.ZERO : liabilityLimit;
    liability.invoiceDeduct = invoiceDeduct == null ? java.math.BigDecimal.ZERO : invoiceDeduct;
    liability.adjustDeduct = adjustDeduct == null ? java.math.BigDecimal.ZERO : adjustDeduct;
    liability.dailyLimit = dailyLimit == null ? java.math.BigDecimal.ZERO : dailyLimit;
    liability.dailyLimitBasis =
        dailyLimitBasis == null || dailyLimitBasis.isBlank() ? "NONE" : dailyLimitBasis;
    liability.limitMode = "NONE";
    liability.limitHospitalCodes = "";
    liability.limitDiagnosisCodes = "";
    liability.socialOnly = socialOnly == null ? 1 : socialOnly;
    liability.waitingPeriod = waitingPeriod == null ? 0 : waitingPeriod;
    liability.status = status == null ? "DRAFT" : status;
    java.time.Instant now = java.time.Instant.now();
    liability.createdAt = now;
    liability.updatedAt = now;
    return liability;
  }

  /**
   * 含适用限定（V1 §2 要素 6）的完整责任配置工厂。
   *
   * <p>限定只覆盖医院 / 病种两维——其余四维（区域/医保/药品/诊疗）需要外部主数据，本仓库无法闭环， 因此<b>没有对应列</b>：表达不出来，就不会出现"配了但引擎不读"的静默失效。
   *
   * @param limitMode {@code NONE} / {@code WHITELIST} / {@code BLACKLIST}；{@code NONE} 时名单被忽略
   * @param limitHospitalCodes 医院编码串（空 = 该维度不限定）
   * @param limitDiagnosisCodes 病种编码串（空 = 该维度不限定）
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
      String limitMode,
      String limitHospitalCodes,
      String limitDiagnosisCodes,
      Integer socialOnly,
      Integer waitingPeriod,
      String status) {
    AdjLiability liability =
        of(
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
            dailyLimit,
            dailyLimitBasis,
            socialOnly,
            waitingPeriod,
            status);
    liability.limitMode = limitMode == null || limitMode.isBlank() ? "NONE" : limitMode;
    liability.limitHospitalCodes = limitHospitalCodes == null ? "" : limitHospitalCodes;
    liability.limitDiagnosisCodes = limitDiagnosisCodes == null ? "" : limitDiagnosisCodes;
    return liability;
  }

  /**
   * 含目录准入（缺口 ① / 适用限定「药品·诊疗」维度）的完整责任配置工厂。
   *
   * <p>目录准入与日限额同一套纪律：<b>策略与目录编码必须同配或同不配</b>。只配一边在装载期即被 {@link
   * com.bone.claim.domain.model.adjustment.engine.CatalogAdmission#of} 拒绝——因为"配了不生效"比"没配"更危险：
   * 后者至少在文档里是缺口，前者在配置页、评审和代码里都显示"已支持"。
   *
   * @param catalogPolicy {@code NONE}（不查目录）/ {@code MANDATORY}（每条明细必须查到结论）
   * @param catalogCode 目录编码（如 {@code NATIONAL_2024}）；{@code NONE} 时须为空
   */
  public static AdjLiability of(
      Long id,
      Long tenantId,
      String liabilityUuid,
      String liabilityVersion,
      Long planId,
      Long coverageId,
      String liabilityCode,
      String liabilityType,
      java.math.BigDecimal payPercent,
      java.math.BigDecimal liabilityDeduct,
      java.math.BigDecimal liabilityLimit,
      java.math.BigDecimal invoiceDeduct,
      java.math.BigDecimal adjustDeduct,
      java.math.BigDecimal dailyLimit,
      String dailyLimitBasis,
      String limitMode,
      String limitHospitalCodes,
      String limitDiagnosisCodes,
      String catalogPolicy,
      String catalogCode,
      Integer socialOnly,
      Integer waitingPeriod,
      String status) {
    AdjLiability liability =
        of(
            id,
            tenantId,
            liabilityUuid,
            liabilityVersion,
            planId,
            coverageId,
            liabilityCode,
            liabilityType,
            payPercent,
            liabilityDeduct,
            liabilityLimit,
            invoiceDeduct,
            adjustDeduct,
            dailyLimit,
            dailyLimitBasis,
            socialOnly,
            waitingPeriod,
            status);
    liability.catalogPolicy =
        catalogPolicy == null || catalogPolicy.isBlank() ? "NONE" : catalogPolicy;
    liability.catalogCode = catalogCode == null ? "" : catalogCode;
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

  /** 目录准入策略（缺口 ①）；缺省 {@code NONE}（不查目录，与历史行为一致）。 */
  public String catalogPolicy() {
    return catalogPolicy == null || catalogPolicy.isBlank() ? "NONE" : catalogPolicy;
  }

  /** 目录编码（缺口 ①）；未配为空串。 */
  public String catalogCode() {
    return catalogCode == null ? "" : catalogCode;
  }

  /**
   * 适用限定方式（V1 §2 要素 6）：{@code NONE} / {@code WHITELIST} / {@code BLACKLIST}。
   *
   * <p>{@code NONE} 为默认，与历史配置行为完全一致（不设限）；只有显式配了模式且配了名单才生效。
   */
  public String limitMode() {
    return limitMode == null || limitMode.isBlank() ? "NONE" : limitMode;
  }

  /** 适用限定——医院编码名单（逗号/顿号/分号/空白分隔；仅在被限定的维度上判定）。 */
  public String limitHospitalCodes() {
    return limitHospitalCodes == null ? "" : limitHospitalCodes;
  }

  /** 适用限定——病种编码名单（同上）。 */
  public String limitDiagnosisCodes() {
    return limitDiagnosisCodes == null ? "" : limitDiagnosisCodes;
  }

  /**
   * 发布责任（DRAFT → ACTIVE）。
   *
   * <p><b>版本化不变式（§8.2「已生效版本不可改」）</b>：本方法只翻转状态，不修改任何参数； 真正"改已生效版本"的语义由 {@code
   * LiabilityConfigApplicationService} 以"生成新版本草稿"实现， 旧版本冻结保留。已下线的责任不可再发布。
   */
  public void publish() {
    if ("RETIRED".equals(status)) {
      throw new DomainException("责任已下线，不可发布");
    }
    this.status = "ACTIVE";
  }

  /** 下线责任（ACTIVE/DRAFT → RETIRED）：不再参与理算，历史版本保留可查。 */
  public void retire() {
    this.status = "RETIRED";
  }

  /**
   * 原地应用编辑（DRAFT 草稿可改；ACTIVE 走新版本由服务层负责）。
   *
   * <p>只改参数、不动 id/uuid/版本/状态，并保留 SDK 管理的 {@link #version}（乐观锁）， 使并发编辑在 flush 时抛 {@code
   * OptimisticLockingFailureException} 而非静默覆盖。
   */
  public void applyEdit(
      String liabilityCode,
      String liabilityType,
      java.math.BigDecimal payPercent,
      java.math.BigDecimal liabilityDeduct,
      java.math.BigDecimal liabilityLimit,
      java.math.BigDecimal invoiceDeduct,
      java.math.BigDecimal adjustDeduct,
      java.math.BigDecimal dailyLimit,
      String dailyLimitBasis,
      String limitMode,
      String limitHospitalCodes,
      String limitDiagnosisCodes,
      String catalogPolicy,
      String catalogCode,
      Integer socialOnly,
      Integer waitingPeriod,
      Long coverageId) {
    this.liabilityCode = liabilityCode == null ? "" : liabilityCode;
    this.liabilityType = liabilityType == null ? "REIMBURSEMENT" : liabilityType;
    this.payPercent = payPercent == null ? BigDecimal.ZERO : payPercent;
    this.liabilityDeduct = liabilityDeduct == null ? java.math.BigDecimal.ZERO : liabilityDeduct;
    this.liabilityLimit = liabilityLimit == null ? java.math.BigDecimal.ZERO : liabilityLimit;
    this.invoiceDeduct = invoiceDeduct == null ? java.math.BigDecimal.ZERO : invoiceDeduct;
    this.adjustDeduct = adjustDeduct == null ? java.math.BigDecimal.ZERO : adjustDeduct;
    this.dailyLimit = dailyLimit == null ? java.math.BigDecimal.ZERO : dailyLimit;
    this.dailyLimitBasis =
        dailyLimitBasis == null || dailyLimitBasis.isBlank() ? "NONE" : dailyLimitBasis;
    this.limitMode = limitMode == null || limitMode.isBlank() ? "NONE" : limitMode;
    this.limitHospitalCodes = limitHospitalCodes == null ? "" : limitHospitalCodes;
    this.limitDiagnosisCodes = limitDiagnosisCodes == null ? "" : limitDiagnosisCodes;
    this.catalogPolicy = catalogPolicy == null || catalogPolicy.isBlank() ? "NONE" : catalogPolicy;
    this.catalogCode = catalogCode == null ? "" : catalogCode;
    this.socialOnly = socialOnly == null ? 1 : socialOnly;
    this.waitingPeriod = waitingPeriod == null ? 0 : waitingPeriod;
    this.coverageId = coverageId;
    this.updatedAt = java.time.Instant.now();
  }
}
