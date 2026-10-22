package com.bone.claim.domain.model.claim;

import com.bone.claim.domain.model.claim.event.ClaimDecisionMadeEvent;
import com.bone.claim.domain.model.claim.event.ClaimStatusChangedEvent;
import com.bone.claim.domain.model.claim.event.ClaimSubmittedEvent;
import com.bone.claim.domain.model.claim.valueobject.ClaimStage;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.claim.domain.model.claim.valueobject.Decision;
import com.bone.claim.domain.model.claim.valueobject.HangUpType;
import com.bone.claim.domain.model.claim.valueobject.ItemCategory;
import com.bone.core.annotation.Transient;
import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Cascade;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 赔案聚合根（A1，多租户 + 领域事件）。
 *
 * <p>状态机 = 工程落地版 §6.1/§6.2（pk-tpa 数字码体系移植）；本类是该状态机的**唯一**推进入口—— 状态解释权归聚合（意图揭示方法），应用层不得直接比较 {@code
 * getStatus()}（反贫血 E-6.4）。
 *
 * <p>对外投影：{@link ClaimStatus#toExternal()} 只产出 {@code PENDING_EVIDENCE}/{@code REVIEWING}
 * 两值，终态不回传状态枚举 （delivery/05 唯一真源）。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_claim_case")
public class ClaimCase extends TenantAggregateRoot<Long> {

  /** 报案号（对外展示/客服检索键，与物理 id 分离；由 {@code CL + yyyyMMdd + id} 生成）。 */
  @Column(name = "claim_no", nullable = false)
  private String claimNo;

  /** 写接口幂等键（Idempotency-Key 请求头值，服务端登记）。 */
  @Column(name = "idempotency_key", nullable = false)
  private String idempotencyKey;

  @Column(name = "policy_no", nullable = false)
  private String policyNo;

  @Column(name = "plan_id", nullable = false)
  private Long planId;

  @Column(name = "insured_id_hash", nullable = false)
  private String insuredIdHash;

  @Column(name = "claim_type", nullable = false)
  private String claimType;

  /** 状态（DB 存枚举 name，如 {@code ACCEPTED}；pk-tpa 数字码经 {@link #status()}.code() 供存量桥接）。 */
  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "status_sub")
  private String statusSub;

  /** 业务阶段（DB 存枚举 name，与 status 正交）。 */
  @Column(name = "stage", nullable = false)
  private String stage;

  /** 挂起状态（0 未挂起 / 1 已挂起）；挂起类型见 hangUpType（正交机制，不改状态码）。 */
  @Column(name = "hang_up_status", nullable = false)
  private int hangUpStatus;

  @Column(name = "hang_up_type")
  private String hangUpType;

  @Column(name = "invoice_code")
  private String invoiceCode;

  @Column(name = "invoice_no")
  private String invoiceNo;

  @Column(name = "out_insure_time", nullable = false)
  private Instant outInsureTime;

  /** 进入终态（结案/拒赔生效）的时间戳，用于申诉 48h 时限起算（§6.6）。 */
  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "source_channel")
  private String sourceChannel;

  @Column(name = "source_system", nullable = false)
  private String sourceSystem;

  /** 批次号（报案号生成前缀 + 复制赔案分组）。 */
  @Column(name = "batch_no")
  private String batchNo;

  @Column(name = "claim_amount", nullable = false)
  private BigDecimal claimAmount;

  /** 核定金额（理算后回填；核赔决策通过时锁定）。 */
  @Column(name = "approved_amount")
  private BigDecimal approvedAmount;

  /** 第一审核人（核赔）。 */
  @Column(name = "first_reviewer_id")
  private String firstReviewerId;

  /** 第二复核人（大额 ≥1 万双人复核）。 */
  @Column(name = "second_reviewer_id")
  private String secondReviewerId;

  /** 拒赔五要素（REJECTED 必填，落 comment）。 */
  @Column(name = "reject_comment")
  private String rejectComment;

  /** 受控拒赔原因码（§8.3 01-13 + 20/30/40），禁自由文本。 */
  @Column(name = "reject_type")
  private String rejectType;

  /** 受理时锁定的计划版本（历史案件复算确定性，§4.1）。 */
  @Column(name = "plan_version")
  private String planVersion;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** 乐观锁版本号（SDK 原生 @Version，ADR-0031 D2）：SDK 维护递增与冲突检测，领域层不手写。 */
  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  /**
   * 聚合内明细集合（{@code @Transient} 不映射列；{@code @Cascade} 由 SDK 根保存时级联）。
   *
   * <p><b>孤儿清除必须关闭</b>：明细创建后不可变，{@code findById} 不回填集合，状态更新时内存 items 恒为空， 开启孤儿清除会把 DB
   * 既有明细静默清空（blueprint Order 同款踩坑，注释保留）。
   */
  @Transient
  @Cascade(foreignKey = "claim_id", orphanRemoval = false)
  private List<ClaimItem> items = new ArrayList<>();

  // ===================== 工厂方法 =====================

  /**
   * 报案建案（DRAFT）。
   *
   * <p>此步只落草稿：受理签收（{@link #accept}）才进入 7 并对外可见。id 由调用方预分配（雪花），报案号由 id 派生，全局唯一且可重复推导（对账友好）。
   */
  public static ClaimCase draft(
      long id,
      Long tenantId,
      String policyNo,
      Long planId,
      String insuredIdHash,
      String claimType,
      String invoiceCode,
      String invoiceNo,
      Instant outInsureTime,
      String batchNo,
      String planVersion,
      List<ClaimItem> items) {
    if (policyNo == null || policyNo.isBlank()) {
      throw new DomainException("保单号不能为空");
    }
    if (planId == null) {
      throw new DomainException("计划ID不能为空");
    }
    if (insuredIdHash == null || insuredIdHash.isBlank()) {
      throw new DomainException("被保人假名化ID不能为空（服务端计算，客户端不传明文）");
    }
    if (outInsureTime == null) {
      throw new DomainException("出险时间不能为空");
    }
    if (items == null || items.isEmpty()) {
      throw new DomainException("报案至少需要一个费用明细项");
    }
    BigDecimal total = BigDecimal.ZERO;
    for (ClaimItem item : items) {
      total = total.add(item.getAmount());
    }
    ClaimCase claim = new ClaimCase();
    claim.setId(id);
    claim.setTenantId(tenantId);
    claim.claimNo = generateClaimNo(id);
    claim.policyNo = policyNo;
    claim.planId = planId;
    claim.insuredIdHash = insuredIdHash;
    claim.claimType = claimType == null || claimType.isBlank() ? "OUTPATIENT" : claimType;
    claim.invoiceCode = invoiceCode == null ? "" : invoiceCode;
    claim.invoiceNo = invoiceNo == null ? "" : invoiceNo;
    claim.outInsureTime = outInsureTime;
    claim.batchNo = batchNo;
    claim.planVersion = planVersion;
    claim.claimAmount = total;
    claim.status = ClaimStatus.DRAFT.name();
    claim.stage = ClaimStage.SIGNING.name();
    claim.sourceSystem = "self";
    claim.items = new ArrayList<>(items);
    claim.version = 0L;
    Instant now = Instant.now();
    claim.createdAt = now;
    claim.updatedAt = now;
    return claim;
  }

  /** 报案号生成策略：{@code CL + yyyyMMdd(东八区) + 雪花 id}——业务时区固定，禁止跟随宿主机默认时区。 */
  private static String generateClaimNo(long id) {
    return "CL"
        + java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
            .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
        + id;
  }

  // ===================== 状态机（§6.2 合法转换） =====================

  /**
   * 受理签收（DRAFT → 7，含存量旁路 sync 导入）。
   *
   * <p>幂等：已 ACCEPTED 直接返回 false（存量系统重复推送是必然，不得报错）。
   */
  public boolean accept() {
    if (status() == ClaimStatus.ACCEPTED) {
      return false;
    }
    if (status() != ClaimStatus.DRAFT) {
      throw new DomainException("只有草稿状态可以受理签收，当前状态: " + status().code());
    }
    transition(ClaimStatus.ACCEPTED, "SIGN");
    addDomainEvent(new ClaimSubmittedEvent(getId(), getTenantId(), claimNo, Instant.now()));
    return true;
  }

  /**
   * 材料完整 → 分配初审（7 → 10）。
   *
   * <p>材料不齐走 7 → 21（{@link #requestEvidence}）。
   */
  public void allocateToPreExam(String operatorId) {
    requireStatus(ClaimStatus.ACCEPTED, "受理后才能分配初审");
    transition(ClaimStatus.PENDING_PRE_EXAM, "ASSIGN", operatorId);
  }

  /** 缺件补录（7/12/41 → 21；对外 PENDING_EVIDENCE）。 */
  public void requestEvidence(String operatorId) {
    if (status() != ClaimStatus.ACCEPTED
        && status() != ClaimStatus.PRE_EXAMINED
        && status() != ClaimStatus.ADJUSTING) {
      throw new DomainException("当前状态不支持补件流转，当前状态: " + status().code());
    }
    transition(ClaimStatus.EVIDENCE_SUBMITTING, "REQUEST_MORE_EVIDENCE", operatorId);
  }

  /** 补录完成回流（21 → 10）。 */
  public void evidenceSubmitted(String operatorId) {
    requireStatus(ClaimStatus.EVIDENCE_SUBMITTING, "补录中才能回流");
    transition(ClaimStatus.PENDING_PRE_EXAM, "EVIDENCE_UPLOAD", operatorId);
  }

  /** 进入初审（10 → 11 人工 / 13 自动化，按路径二选一）。 */
  public void startPreExam(boolean automated, String operatorId) {
    requireStatus(ClaimStatus.PENDING_PRE_EXAM, "待初审分配才能开始初审");
    transition(
        automated ? ClaimStatus.AUTO_PRE_EXAMINING : ClaimStatus.PRE_EXAMINING,
        "ASSIGN",
        operatorId);
  }

  /** 决策图完成（11/13 → 12，产出 DecisionResult）。 */
  public void completePreExam(String operatorId) {
    if (status() != ClaimStatus.PRE_EXAMINING && status() != ClaimStatus.AUTO_PRE_EXAMINING) {
      throw new DomainException("初审中才能完成初审，当前状态: " + status().code());
    }
    transition(ClaimStatus.PRE_EXAMINED, "DECISION_AUTO", operatorId);
  }

  /**
   * 决策图完成并回填理算核定金额（11/13 → 12，推荐入口）。
   *
   * <p>为什么不拆成 {@code completePreExam(operator)} + {@code applyApprovedAmount(amount)} 两步：
   * 拆开后「状态已跃迁到 12 但金额仍为空」是一个可被并发读取的中间态，自动通过路径 （{@link #autoApprove}）依赖核定金额，中间态会让放行判断落空；且金额不变量 （≤
   * 申请金额）必须在跃迁**前**校验，否则会出现"先成已初审、再发现超赔"。 故合并为单一领域行为，应用层一次调用。
   */
  public void completePreExam(String operatorId, BigDecimal approvedAmount) {
    if (status() != ClaimStatus.PRE_EXAMINING && status() != ClaimStatus.AUTO_PRE_EXAMINING) {
      throw new DomainException("初审中才能完成初审，当前状态: " + status().code());
    }
    if (approvedAmount != null) {
      applyApprovedAmount(approvedAmount);
    }
    transition(ClaimStatus.PRE_EXAMINED, "DECISION_AUTO", operatorId);
  }

  /** 转人工核赔（12 → 40；SUGGEST_REJECT 的人工确认落点，一律走此路径，不由 12 直落 45）。 */
  public void escalateToAdjust(String operatorId) {
    requireStatus(ClaimStatus.PRE_EXAMINED, "已初审才能转人工核赔");
    transition(ClaimStatus.PENDING_ADJUST, "DECISION_MANUAL", operatorId);
  }

  /** 领取核赔任务（40 → 41）。 */
  public void claimAdjustTask(String reviewerId) {
    requireStatus(ClaimStatus.PENDING_ADJUST, "待核赔才能领取");
    if (reviewerId == null || reviewerId.isBlank()) {
      throw new DomainException("核赔人不能为空");
    }
    this.firstReviewerId = reviewerId;
    transition(ClaimStatus.ADJUSTING, "ASSIGN", reviewerId);
  }

  /**
   * 人工核赔决策（41 → 42 通过 / 45 拒赔生效 / 21 补件）。
   *
   * <p>拒赔必填五要素（{@code comment}）与受控原因码（{@code rejectType}）；通过时锁定核定金额（≤ 申请金额不变量）。
   */
  public void decide(
      Decision decision,
      String reviewerId,
      String comment,
      String rejectType,
      BigDecimal approvedAmount) {
    if (status() != ClaimStatus.ADJUSTING) {
      throw new DomainException("核赔中才能提交审核决策，当前状态: " + status().code());
    }
    if (reviewerId == null || reviewerId.isBlank()) {
      throw new DomainException("复核人不能为空");
    }
    this.firstReviewerId = reviewerId;
    switch (decision) {
      case APPROVED -> {
        if (approvedAmount == null || approvedAmount.signum() < 0) {
          throw new DomainException("核定金额不能为负");
        }
        if (approvedAmount.compareTo(claimAmount) > 0) {
          throw new DomainException("核定金额不能超过申请金额（不变量：赔付金额 ≤ 索赔金额）");
        }
        this.approvedAmount = approvedAmount;
        transition(ClaimStatus.ADJUSTED, "DECISION_MANUAL", reviewerId);
      }
      case REJECTED -> {
        if (comment == null || comment.isBlank()) {
          throw new DomainException("拒赔必填五要素（事实认定+条款依据+证据位置+申诉渠道+法定时限）");
        }
        if (rejectType == null || rejectType.isBlank()) {
          throw new DomainException("拒赔必须带受控原因码（§8.3），禁自由文本");
        }
        this.rejectComment = comment;
        this.rejectType = rejectType;
        transition(ClaimStatus.CLOSED_REJECTED, "REJECT", reviewerId);
      }
      case REQUEST_MORE_EVIDENCE -> transition(
          ClaimStatus.EVIDENCE_SUBMITTING, "REQUEST_MORE_EVIDENCE", reviewerId);
      default -> throw new DomainException("未知决策: " + decision);
    }
    addDomainEvent(
        new ClaimDecisionMadeEvent(
            getId(), getTenantId(), claimNo, decision, reviewerId, comment, Instant.now()));
  }

  /** 大额进入双人复核（42 → 44；≥1 万）。 */
  public void submitForSecondReview(String reviewerId) {
    requireStatus(ClaimStatus.ADJUSTED, "已审核才能进入双人复核");
    if (reviewerId == null || reviewerId.isBlank()) {
      throw new DomainException("复核人不能为空");
    }
    if (reviewerId.equals(firstReviewerId)) {
      throw new DomainException("双人复核必须由非第一审核人执行（§11.10 职责分离）");
    }
    this.secondReviewerId = reviewerId;
    transition(ClaimStatus.REVIEWING, "ASSIGN", reviewerId);
  }

  /** 复核通过（44 → 50 结案入口；资金链路由 A7 订阅推进，本聚合不碰资金）。 */
  public void completeSecondReview(boolean approved, String reviewerId) {
    requireStatus(ClaimStatus.REVIEWING, "复核中才能提交复核结论");
    if (!reviewerId.equals(secondReviewerId)) {
      throw new DomainException("复核人必须是登记的复核人");
    }
    transition(approved ? ClaimStatus.FINISHED : ClaimStatus.CLOSED_REJECTED, "REVIEW", reviewerId);
  }

  /** 自动通过（12 → 50，不经人工审核态 40/41/42/44；仍经资金 Saga，见 §6.3）。 */
  public void autoApprove(String operatorId) {
    requireStatus(ClaimStatus.PRE_EXAMINED, "已初审才能自动通过");
    if (approvedAmount == null) {
      throw new DomainException("自动通过前必须回填理算核定金额");
    }
    transition(ClaimStatus.FINISHED, "DECISION_AUTO", operatorId);
  }

  /** 超期关单（21 → 45；§6.6 SUBMITTING 超 30d 未补件）。 */
  public void closeForStale(String operatorId) {
    requireStatus(ClaimStatus.EVIDENCE_SUBMITTING, "补录中才能超期关单");
    transition(ClaimStatus.CLOSED_REJECTED, "CLOSE", operatorId);
  }

  // ===================== 挂起（正交机制，§6.5） =====================

  /** 挂起（阻断 SLA 计时与自动推进；不改状态码）。 */
  public void hangUp(HangUpType type, String operatorId) {
    if (type == null) {
      throw new DomainException("挂起类型不能为空");
    }
    if (this.hangUpStatus == 1) {
      throw new DomainException("案件已挂起，须先恢复");
    }
    this.hangUpStatus = 1;
    this.hangUpType = type.name();
    this.updatedAt = Instant.now();
    addDomainEvent(
        new ClaimStatusChangedEvent(
            getId(),
            getTenantId(),
            claimNo,
            status().code(),
            status().code(),
            "HANG_UP",
            operatorId,
            Instant.now()));
  }

  /**
   * 恢复挂起（回挂起前状态码）。
   *
   * <p>{@code FRAUD} 挂起仅风控岗有权恢复——调用方（应用层）负责校验角色，聚合只保证状态一致性。
   */
  public void resume(String operatorId) {
    if (this.hangUpStatus != 1) {
      throw new DomainException("案件未挂起");
    }
    this.hangUpStatus = 0;
    this.hangUpType = null;
    this.updatedAt = Instant.now();
    addDomainEvent(
        new ClaimStatusChangedEvent(
            getId(),
            getTenantId(),
            claimNo,
            status().code(),
            status().code(),
            "RESUME",
            operatorId,
            Instant.now()));
  }

  // ===================== 意图揭示查询（状态解释权归聚合） =====================

  /** 是否可分配初审。 */
  public boolean isAllocatable() {
    return status() == ClaimStatus.ACCEPTED && hangUpStatus != 1;
  }

  /** 是否待人工核赔（工作台待办口径）。 */
  public boolean isPendingAdjust() {
    return status() == ClaimStatus.PENDING_ADJUST;
  }

  /** 是否已进入人工审核链（40/41/42/44）。 */
  public boolean inManualReview() {
    return status() == ClaimStatus.PENDING_ADJUST
        || status() == ClaimStatus.ADJUSTING
        || status() == ClaimStatus.ADJUSTED
        || status() == ClaimStatus.REVIEWING;
  }

  /** 是否需双人复核（≥1 万，§11.7 不变量 5 的写入路径前置）。 */
  public boolean requiresSecondReview() {
    return approvedAmount != null && approvedAmount.compareTo(SECOND_REVIEW_THRESHOLD) >= 0;
  }

  /** 是否已终态。 */
  public boolean finished() {
    return status().terminal();
  }

  /** 进入终态时间戳（申诉 48h 时限起算点；未终态为 null）。 */
  public Instant closedAt() {
    return closedAt;
  }

  /** 发起申诉：终态（结案/拒赔生效）→ 申诉中（60），48h 时限由应用层校验（§6.6）。 */
  public void enterAppeal(String operatorId) {
    if (!finished()) {
      throw new DomainException("仅终态案件可发起申诉，当前状态: " + status().code());
    }
    transition(ClaimStatus.APPEALING, "APPEAL", operatorId);
  }

  /** 申诉维持原结论：申诉中（60）→ 拒赔生效/结案终态（45）。 */
  public void resolveAppealUpheld(String operatorId) {
    requireStatus(ClaimStatus.APPEALING, "申诉维持须处于申诉中");
    transition(ClaimStatus.CLOSED_REJECTED, "APPEAL_UPHELD", operatorId);
  }

  /** 申诉推翻重启审核：申诉中（60）→ 核赔中（41）。 */
  public void resolveAppealReopen(String operatorId) {
    requireStatus(ClaimStatus.APPEALING, "申诉重启审核须处于申诉中");
    transition(ClaimStatus.ADJUSTING, "APPEAL_REOPEN", operatorId);
  }

  /** 对外状态两值投影（终态返回 null，由 decision/fundingStage 表达）。 */
  public String externalStatus() {
    return status().toExternal();
  }

  private static final java.math.BigDecimal SECOND_REVIEW_THRESHOLD =
      new java.math.BigDecimal("10000");

  // ===================== 内部 =====================

  /**
   * 状态跃迁唯一入口：校验合法性 → 改状态 → 同步阶段 → 发 {@link ClaimStatusChangedEvent}。
   *
   * <p>转换表（工程落地版 §6.2）由本方法体内各业务方法的守卫条件表达，不单独建表—— 表在文档、守卫在代码，两侧同源维护。
   */
  private void transition(ClaimStatus to, String actionCode, String operatorId) {
    ClaimStatus from = status();
    if (from == to) {
      throw new DomainException("状态未变化，禁止空跃迁: " + from.code());
    }
    this.status = to.name();
    this.stage = stageOf(to).name();
    this.updatedAt = Instant.now();
    if (to.terminal() && !from.terminal()) {
      this.closedAt = Instant.now();
    }
    addDomainEvent(
        new ClaimStatusChangedEvent(
            getId(),
            getTenantId(),
            claimNo,
            from.code(),
            to.code(),
            actionCode,
            operatorId,
            Instant.now()));
  }

  /** 无操作人的重载（系统动作）。 */
  private void transition(ClaimStatus to, String actionCode) {
    transition(to, actionCode, "SYSTEM");
  }

  /** 状态 → 阶段映射（§6.1 状态码表的 stage 列）。 */
  private static ClaimStage stageOf(ClaimStatus status) {
    return switch (status) {
      case DRAFT, ACCEPTED, EVIDENCE_SUBMITTING -> ClaimStage.SIGNING;
      case PENDING_PRE_EXAM, PRE_EXAMINING, AUTO_PRE_EXAMINING, PRE_EXAMINED -> ClaimStage.PRE_EXAM;
      case PENDING_ADJUST, ADJUSTING, ADJUSTED -> ClaimStage.AUDITING;
      case REVIEWING, APPEALING -> ClaimStage.REVIEWING;
      case FINISHED, CLOSED_REJECTED -> ClaimStage.FINISH;
    };
  }

  private void requireStatus(ClaimStatus expected, String action) {
    if (status() != expected) {
      throw new DomainException(action + "，当前状态: " + status().code());
    }
  }

  // ===================== 持久化访问器（DB 存 name，内存用枚举） =====================

  /** 状态枚举（DB name → 枚举；兼容历史数据 null 视为 DRAFT）。 */
  public ClaimStatus status() {
    return status == null ? ClaimStatus.DRAFT : ClaimStatus.ofName(status);
  }

  /** 业务阶段枚举。 */
  public ClaimStage stageEnum() {
    return stage == null ? ClaimStage.SIGNING : ClaimStage.ofName(stage);
  }

  /** 挂起类型枚举（未挂起为 null）。 */
  public HangUpType hangUpTypeEnum() {
    return hangUpType == null ? null : HangUpType.ofName(hangUpType);
  }

  /**
   * 回填理算核定金额（决策图完成后由应用层调用，聚合只校验不变量——金额计算在 A6 理算域，本聚合不重算）。
   *
   * <p>与 {@link #decide} 的 APPROVED 分支同口径：≤ 申请金额、非负。
   */
  public void applyApprovedAmount(java.math.BigDecimal approvedAmount) {
    if (approvedAmount == null || approvedAmount.signum() < 0) {
      throw new DomainException("核定金额不能为负");
    }
    if (approvedAmount.compareTo(claimAmount) > 0) {
      throw new DomainException("核定金额不能超过申请金额（不变量：赔付金额 ≤ 索赔金额）");
    }
    this.approvedAmount = approvedAmount;
    this.updatedAt = Instant.now();
  }

  /** 聚合内明细集合（包级可见，理由同 blueprint Order：findById 不回填，读侧走仓储投影）。 */
  List<ClaimItem> items() {
    return Collections.unmodifiableList(items);
  }

  /** 申请金额（Money 口径由调用方按 DECIMAL(12,2) 使用）。 */
  public java.math.BigDecimal claimAmount() {
    return claimAmount;
  }

  /** 明细类别集合（供测试与读侧校验）。 */
  public List<ItemCategory> itemCategories() {
    List<ItemCategory> categories = new ArrayList<>();
    for (ClaimItem item : items) {
      categories.add(item.categoryEnum());
    }
    return Collections.unmodifiableList(categories);
  }
}
