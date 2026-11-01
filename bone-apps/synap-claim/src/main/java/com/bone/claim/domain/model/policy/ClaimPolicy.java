package com.bone.claim.domain.model.policy;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 保单聚合根（{@code t_claim_policy}）——被保人身份 + 承保期间 + 规则开关。
 *
 * <p><b>为什么本仓需要这张表</b>：设计文档 §9.3 早已定义 {@code adj_policy}，但它<b>从未落地任何一行代码</b>， 导致三个连锁空洞：
 *
 * <ul>
 *   <li>报案前置校验（{@code SC_CLAIM_POLICY_NOT_ACTIVE}）在生产<b>必然拒收</b>——{@code PolicyQueryPort} 此前只有进程内存
 *       {@code ConcurrentHashMap} 桩，且只有 {@code @Profile("dev")} 的 seeder 灌两条种子， 非 dev 环境查任何保单都返回空；
 *   <li>保单号只是 {@code t_claim_case.policy_no} 里的裸字符串，没有主数据 ⇒ 填错只能到受理时才发现；
 *   <li>额度年度按保单年度（{@code POLICY_TERM}）裁决时需要保单期间，而期间只存在于内存桩里。
 * </ul>
 *
 * <p><b>规则开关三态</b>（{@link #configStatus}）对应参考实现 pk-tpa-saas 的"启用/停用保单责任"： {@code INACTIVE} 未启用 /
 * {@code ACTIVE} 启用中 / {@code PAUSE} 停用中。三态而非布尔，是因为运营
 * 需要区分"还没配"与"配了但临时停"——这两者的处置动作完全不同（前者去配置，后者去恢复）。
 *
 * <p><b>被保人清单不存明文证件</b>：{@link #insuredSnapshot} 只存服务端 HMAC 假名化后的 ID 数组， 与 {@code
 * t_quota_account.insured_id_hash} 同一口径。库里一旦落明文证件号，PIA/数据出境审计即不可通过。
 *
 * <p><b>不变量</b>：生效期间必须 {@code effectiveStart < effectiveEnd}；配置状态只能按 {@code INACTIVE → ACTIVE →
 * PAUSE → ACTIVE} 流转（不允许从 PAUSE 直跳回 INACTIVE，那等于"停用后又要重新配"， 应走编辑路径而不是开关）。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_claim_policy")
public class ClaimPolicy extends TenantAggregateRoot<Long> {

  /** 规则开关：未启用。 */
  public static final String CONFIG_INACTIVE = "INACTIVE";

  /** 规则开关：启用中。 */
  public static final String CONFIG_ACTIVE = "ACTIVE";

  /** 规则开关：停用中。 */
  public static final String CONFIG_PAUSE = "PAUSE";

  /** 保单类型：团体保单（企业/机构投保，多名被保人共享保单责任与额度池）。 */
  public static final String TYPE_GROUP = "GROUP";

  /** 保单类型：个人保单（自然人投保，被保人为投保人本人，单人单账户）。 */
  public static final String TYPE_INDIVIDUAL = "INDIVIDUAL";

  @Column(name = "policy_no", nullable = false)
  private String policyNo;

  /**
   * 保单类型（{@link #TYPE_GROUP} / {@link #TYPE_INDIVIDUAL}；历史数据为空按 GROUP 处理）。
   *
   * <p><b>为什么是分类字段而非两套模型</b>：团单与个单在配置结构上同构（保单→计划→险种→责任、 额度账户按被保人开立），差异只在业务语义与入口交互（团单批量导入被保人、个单单人）。
   * 拆两套聚合会把承保期间校验、规则开关、额度年度裁决等域规则复制两份——分类字段 + 场景化 交互即可覆盖，且后续按"团单为主"演进不会牵动个单存量数据。
   */
  @Column(name = "policy_type")
  private String policyType;

  @Column(name = "policy_name")
  private String policyName;

  @Column(name = "insurer_name")
  private String insurerName;

  @Column(name = "branch_name")
  private String branchName;

  @Column(name = "insure_name")
  private String insureName;

  @Column(name = "biz_identity_code")
  private String bizIdentityCode;

  @Column(name = "product_type")
  private String productType;

  @Column(name = "effective_start", nullable = false)
  private Instant effectiveStart;

  @Column(name = "effective_end", nullable = false)
  private Instant effectiveEnd;

  @Column(name = "waiting_period")
  private Integer waitingPeriod;

  @Column(name = "policy_version")
  private String policyVersion;

  /** 被保人清单（假名化 ID 数组；不存明文证件）。 */
  @Column(name = "insured_snapshot")
  private String insuredSnapshot;

  @Column(name = "config_status")
  private String configStatus;

  /** 快照过期标记：1 = 拒新报案（存量同步发现上游已变更时置位）。 */
  @Column(name = "stale_flag")
  private Integer staleFlag;

  @Column(name = "synced_at", nullable = false)
  private Instant syncedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version")
  private Long version;

  private ClaimPolicy(
      Long id,
      Long tenantId,
      String policyNo,
      String policyName,
      String insurerName,
      String branchName,
      String insureName,
      String bizIdentityCode,
      String productType,
      String policyType,
      Instant effectiveStart,
      Instant effectiveEnd,
      int waitingPeriod,
      String policyVersion,
      String insuredSnapshot,
      String configStatus,
      int staleFlag,
      Instant syncedAt) {
    setId(id);
    setTenantId(tenantId);
    this.policyNo = policyNo;
    this.policyType = policyType;
    this.policyName = orEmpty(policyName);
    this.insurerName = orEmpty(insurerName);
    this.branchName = orEmpty(branchName);
    this.insureName = orEmpty(insureName);
    this.bizIdentityCode = orEmpty(bizIdentityCode);
    this.productType = orEmpty(productType);
    this.effectiveStart = effectiveStart;
    this.effectiveEnd = effectiveEnd;
    this.waitingPeriod = waitingPeriod;
    this.policyVersion = orEmpty(policyVersion);
    this.insuredSnapshot = insuredSnapshot;
    this.configStatus = configStatus;
    this.staleFlag = staleFlag;
    this.syncedAt = syncedAt;
    this.createdAt = syncedAt;
    this.updatedAt = syncedAt;
  }

  /** 新建保单（默认"未启用"——配置面刚建出来，责任多半还没配）。 */
  public static ClaimPolicy open(
      Long id,
      Long tenantId,
      String policyNo,
      String policyName,
      String insurerName,
      String branchName,
      String insureName,
      String bizIdentityCode,
      String productType,
      String policyType,
      Instant effectiveStart,
      Instant effectiveEnd,
      int waitingPeriod,
      String policyVersion,
      String insuredSnapshot,
      Instant syncedAt) {
    if (policyNo == null || policyNo.isBlank()) {
      throw new DomainException("保单号不能为空");
    }
    if (effectiveStart == null || effectiveEnd == null) {
      throw new DomainException("保单生效期间不能为空");
    }
    if (!effectiveStart.isBefore(effectiveEnd)) {
      throw new DomainException("保单生效起必须早于生效止");
    }
    if (waitingPeriod < 0) {
      throw new DomainException("等待期不能为负");
    }
    Instant now = syncedAt == null ? Instant.now() : syncedAt;
    return new ClaimPolicy(
        id,
        tenantId,
        policyNo.trim(),
        policyName,
        insurerName,
        branchName,
        insureName,
        bizIdentityCode,
        productType,
        policyType == null || policyType.isBlank() ? TYPE_GROUP : policyType.trim(),
        effectiveStart,
        effectiveEnd,
        waitingPeriod,
        policyVersion,
        insuredSnapshot,
        CONFIG_INACTIVE,
        0,
        now);
  }

  /** 启用保单责任（INACTIVE/PAUSE → ACTIVE）。 */
  public void activate(String operatorId) {
    if (CONFIG_ACTIVE.equals(configStatus)) {
      return; // 幂等：重复启用不报错，也不产生无谓的 updated_at 抖动
    }
    String from = configStatus;
    this.configStatus = CONFIG_ACTIVE;
    this.updatedAt = Instant.now();
    addDomainEvent(
        new com.bone.claim.domain.model.policy.event.PolicyConfigStatusChangedEvent(
            getId(), getTenantId(), policyNo, from, CONFIG_ACTIVE, operatorId, updatedAt));
  }

  /** 停用保单责任（ACTIVE → PAUSE）。 */
  public void pause(String operatorId) {
    if (CONFIG_PAUSE.equals(configStatus)) {
      return;
    }
    String from = configStatus;
    this.configStatus = CONFIG_PAUSE;
    this.updatedAt = Instant.now();
    addDomainEvent(
        new com.bone.claim.domain.model.policy.event.PolicyConfigStatusChangedEvent(
            getId(), getTenantId(), policyNo, from, CONFIG_PAUSE, operatorId, updatedAt));
  }

  /**
   * 编辑保单可修正字段（不改动保单号与规则开关）。
   *
   * <p><b>启用中的保单锁定承保期间</b>：承保期间是报案前置校验与额度年度裁决的事实源， 启用后改期间会让"此刻在保"与"已受理赔案落在哪一年度"同时失准。故 {@code
   * ACTIVE} 态下若改了生效起止即拒绝，须先 {@link #pause} 再编辑——这条规则把"改期间"与"停收案" 两件事显式绑定，避免无声地污染在途赔案。
   *
   * <p><b>不改保单号</b>：保单号是被保人清单、额度账户、赔案的主数据键，改名会制造孤儿引用； 如需换号应走"停用旧 + 新建"而非原地改键。被保人清单、等待期、描述字段则可随时改。
   */
  public void edit(
      String policyName,
      String insurerName,
      String branchName,
      String insureName,
      String bizIdentityCode,
      String productType,
      String policyType,
      Instant effectiveStart,
      Instant effectiveEnd,
      int waitingPeriod,
      String policyVersion,
      String insuredSnapshot) {
    if (waitingPeriod < 0) {
      throw new DomainException("等待期不能为负");
    }
    boolean datesChanged =
        !Objects.equals(this.effectiveStart, effectiveStart)
            || !Objects.equals(this.effectiveEnd, effectiveEnd);
    if (CONFIG_ACTIVE.equals(configStatus) && datesChanged) {
      throw new DomainException("启用中的保单不允许修改承保期间，请先停用");
    }
    if (effectiveStart == null || effectiveEnd == null) {
      throw new DomainException("保单生效期间不能为空");
    }
    if (!effectiveStart.isBefore(effectiveEnd)) {
      throw new DomainException("保单生效起必须早于生效止");
    }
    this.policyName = orEmpty(policyName);
    this.insurerName = orEmpty(insurerName);
    this.branchName = orEmpty(branchName);
    this.insureName = orEmpty(insureName);
    this.bizIdentityCode = orEmpty(bizIdentityCode);
    this.productType = orEmpty(productType);
    // 保单类型允许改（分类字段，无下游外键引用；历史空值编辑后补全）
    this.policyType = policyType == null || policyType.isBlank() ? TYPE_GROUP : policyType.trim();
    this.effectiveStart = effectiveStart;
    this.effectiveEnd = effectiveEnd;
    this.waitingPeriod = waitingPeriod;
    this.policyVersion = orEmpty(policyVersion);
    this.insuredSnapshot = insuredSnapshot;
    this.updatedAt = Instant.now();
  }

  /** 规则开关状态。 */
  public String configStatus() {
    return configStatus;
  }

  /** 规则是否处于启用态（仅 {@code ACTIVE} 为真——{@code PAUSE} 不接受新报案）。 */
  public boolean isConfigActive() {
    return CONFIG_ACTIVE.equals(configStatus);
  }

  /** 快照是否过期（过期即拒新报案）。 */
  public boolean isStale() {
    return staleFlag != null && staleFlag == 1;
  }

  /**
   * 该保单在给定时刻是否处于承保期间内。
   *
   * <p><b>为什么与"生效中"分开两个概念</b>：报案前置校验问"此刻还能不能收案"（要含 configStatus 与 stale），
   * 而额度年度裁决问"这笔费用落在哪个保单年度"（<b>已满期的保单也要能回答</b>）——两者混用会让 年末发票跨年理赔、已满期保单的补赔/红冲全被误判成"保单不存在"。这是本仓已踩过的坑
   * （{@code PolicyQueryPort#findByPolicy} 与 {@code #findActivePolicy} 并存即为此）。
   */
  public boolean coversPeriod(Instant at) {
    return at != null && !at.isBefore(effectiveStart) && !at.isAfter(effectiveEnd);
  }

  /** 承保起始日（本地时区，供"保单年度"按生效日推算）。 */
  public LocalDate inceptionDate() {
    return effectiveStart.atZone(ZoneId.systemDefault()).toLocalDate();
  }

  /** 承保终止日（本地时区）。 */
  public LocalDate expiryDate() {
    return effectiveEnd.atZone(ZoneId.systemDefault()).toLocalDate();
  }

  /** 等待期天数。 */
  public int waitingPeriodDays() {
    return waitingPeriod == null ? 0 : waitingPeriod;
  }

  private static String orEmpty(String value) {
    return value == null ? "" : value;
  }
}
