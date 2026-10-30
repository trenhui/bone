package com.bone.claim.application;

import com.bone.claim.application.port.out.PolicyQueryPort;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimItem;
import com.bone.claim.domain.model.claim.event.ClaimStatusChangedEvent;
import com.bone.claim.domain.model.claim.projection.ClaimHeadProjection;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.claim.domain.model.claim.valueobject.Decision;
import com.bone.claim.domain.model.claim.valueobject.HangUpType;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimItemRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.DomainException;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.sdk.domain.exception.OptimisticLockingFailureException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 赔案应用层统一门面（Facade）——所有赔案用例的唯一入口（ADR-0028 Application Service First）。
 *
 * <p>适配器（Controller / schedule / messaging）只依赖本类；写操作 = 聚合加载 → 领域方法 → {@code save} → {@code
 * publishFrom}， 读操作 = {@code @Transactional(readOnly = true)} 走仓储读模型（ADR-0030）。
 *
 * <p>并发：聚合根 {@code @Version} 乐观锁（ADR-0031），版本冲突在写入路径捕获后重试 ≤3 次， 超限抛 {@link
 * ClaimErrorCodes#CLAIM_CONCURRENT_CONFLICT}（409）——禁业务层 {@code SELECT ... FOR UPDATE}。
 */
@Service
@RequiredArgsConstructor
public class ClaimCaseApplicationService {

  private final ClaimCaseRepository claimCaseRepository;
  private final ClaimItemRepository claimItemRepository;
  private final PolicyQueryPort policyQueryPort;
  private final TenantPort tenantPort;
  private final DomainEventPublisher domainEventPublisher;

  // ===================== 写操作 =====================

  /**
   * 报案建案并受理签收（DRAFT → 7）。
   *
   * <p>幂等：同租户同 {@code idempotencyKey} 重复提交返回首次案件（协议层另有 {@code
   * com.bone.core.idempotency.IdempotencyService} 快照，此处是业务层第二道防线——两处都不能少：快照防重复执行，业务键防并发双写）。
   */
  /** 报案提交结果（adapter 组装 201 响应的输入，不含协议字段）。 */
  public record SubmittedClaim(Long claimId, String claimNo) {}

  @Transactional
  public SubmittedClaim submit(SubmitClaim command) {
    long tenantId = tenantPort.currentTenantId();
    ClaimCase existing =
        claimCaseRepository.findByIdempotencyKey(tenantId, command.idempotencyKey());
    if (existing != null) {
      return new SubmittedClaim(existing.getId(), existing.getClaimNo());
    }

    // 报案前置校验：保单生效中 + 被保人匹配（§7.1）；快照缺失即拒收，不进入状态机
    Optional<PolicyQueryPort.PolicySnapshot> policy =
        policyQueryPort.findActivePolicy(command.policyNo(), command.planId());
    if (policy.isEmpty()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_POLICY_NOT_ACTIVE, command.policyNo());
    }
    PolicyQueryPort.PolicySnapshot snapshot = policy.get();
    if (!snapshot.insuredIdHash().equals(command.insuredIdHash())) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_INSURED_MISMATCH, command.policyNo());
    }
    if (command.outInsureTime().isBefore(snapshot.effectiveFrom())
        || command.outInsureTime().isAfter(snapshot.effectiveTo())) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_OUT_OF_POLICY_PERIOD, command.policyNo());
    }

    long claimId = DistributedIdGenerator.generateLongId();
    List<ClaimItem> items =
        command.items().stream()
            .map(
                dto -> {
                  ClaimItem item =
                      ClaimItem.of(
                          DistributedIdGenerator.generateLongId(),
                          tenantId,
                          claimId,
                          dto.itemName(),
                          dto.itemCode(),
                          dto.category(),
                          dto.quantity(),
                          dto.unitPrice(),
                          dto.amount());
                  // 科室是行级「次」分组维度（V1 §7.3）：随明细落库，否则"同院同科室"责任无法分组。
                  item.assignDepartment(dto.department());
                  return item;
                })
            .toList();

    ClaimCase claim =
        ClaimCase.draft(
            claimId,
            tenantId,
            command.policyNo(),
            command.planId(),
            command.insuredIdHash(),
            command.claimType(),
            command.invoiceCode(),
            command.invoiceNo(),
            command.outInsureTime(),
            command.batchNo(),
            command.idempotencyKey(),
            snapshot.planVersion(),
            items);
    // 发票维度（开票日期/医院/病种）是「次」分组的依据，必须随报案落库：
    // 只加列不写入等于"配了不生效"，理算侧会一律判定为维度缺失。
    claim.bindInvoiceContext(
        command.invoiceDate(), command.hospitalCode(), command.diagnosisCode());
    // 报案即受理签收（员工端提交 = 已受理；缺件走后续 requestEvidence，不阻塞受理）
    claim.accept();

    claimCaseRepository.insert(claim);
    domainEventPublisher.publishFrom(claim);
    return new SubmittedClaim(claim.getId(), claim.getClaimNo());
  }

  /** 分配初审（7 → 10）。 */
  @Transactional
  public void allocate(long claimId, String operatorId) {
    ClaimCase claim = loadForUpdate(claimId);
    claim.allocateToPreExam(operatorId);
    save(claim);
  }

  /** 开始初审（10 → 11/13）。 */
  @Transactional
  public void startPreExam(long claimId, boolean automated, String operatorId) {
    ClaimCase claim = loadForUpdate(claimId);
    claim.startPreExam(automated, operatorId);
    save(claim);
  }

  /** 决策图完成（11/13 → 12，回填理算核定金额）。 */
  @Transactional
  public void completePreExam(long claimId, BigDecimal approvedAmount, String operatorId) {
    ClaimCase claim = loadForUpdate(claimId);
    claim.completePreExam(operatorId, approvedAmount);
    save(claim);
    backfillItemValidAmounts(claimId, approvedAmount);
  }

  /** 转人工核赔（12 → 40）。 */
  @Transactional
  public void escalate(long claimId, String operatorId) {
    ClaimCase claim = loadForUpdate(claimId);
    claim.escalateToAdjust(operatorId);
    save(claim);
  }

  /** 领取核赔任务（40 → 41）。 */
  @Transactional
  public void claimAdjustTask(long claimId, String reviewerId) {
    ClaimCase claim = loadForUpdate(claimId);
    claim.claimAdjustTask(reviewerId);
    save(claim);
  }

  /**
   * 人工核赔决策（41 → 42/45/21）。
   *
   * <p>通过且 ≥1 万：案件停在 42，由工作台显式发起双人复核（{@link #submitForSecondReview}）—— 本方法不自动进 44，避免"决策即复核"跳过人工确认。
   */
  @Transactional
  public void decide(
      long claimId,
      Decision decision,
      String reviewerId,
      String comment,
      String rejectType,
      BigDecimal approvedAmount) {
    ClaimCase claim = loadForUpdate(claimId);
    claim.decide(decision, reviewerId, comment, rejectType, approvedAmount);
    save(claim);
    if (decision == Decision.APPROVED) {
      backfillItemValidAmounts(claimId, claim.approvedAmount());
    }
  }

  /** 发起双人复核（42 → 44；≥1 万）。 */
  @Transactional
  public void submitForSecondReview(long claimId, String reviewerId) {
    ClaimCase claim = loadForUpdate(claimId);
    claim.submitForSecondReview(reviewerId);
    save(claim);
  }

  /** 复核结论（44 → 50/45）。 */
  @Transactional
  public void completeSecondReview(long claimId, boolean approved, String reviewerId) {
    ClaimCase claim = loadForUpdate(claimId);
    claim.completeSecondReview(approved, reviewerId);
    save(claim);
  }

  /** 自动通过（12 → 50；R-FAST-01 快赔路径）。 */
  @Transactional
  public void autoApprove(long claimId, String operatorId) {
    ClaimCase claim = loadForUpdate(claimId);
    claim.autoApprove(operatorId);
    save(claim);
  }

  /** 缺件补录（→ 21）。 */
  @Transactional
  public void requestEvidence(long claimId, String operatorId) {
    ClaimCase claim = loadForUpdate(claimId);
    claim.requestEvidence(operatorId);
    save(claim);
  }

  /** 补录完成回流（21 → 10）。 */
  @Transactional
  public void evidenceSubmitted(long claimId, String operatorId) {
    ClaimCase claim = loadForUpdate(claimId);
    claim.evidenceSubmitted(operatorId);
    save(claim);
  }

  /**
   * 挂起（§6.5 正交机制）。
   *
   * <p>域层用 {@link DomainException}（"案件已挂起，须先恢复"）表达状态冲突，全局 handler 会把它落成 <b>400 + {@code
   * COMMON_VALIDATION_FAILED}</b>：前端既拿不到可分支的码，柜面看到的还是 "参数校验失败：案件已挂起，须先恢复"——把一个 409
   * 语义的状态冲突说成参数问题。此处按 {@code GlobalExceptionHandler} javadoc 推荐的路径在应用层显式翻译（域层异常保持不动）。
   */
  @Transactional
  public void hangUp(long claimId, HangUpType type, String operatorId) {
    ClaimCase claim = loadForUpdate(claimId);
    try {
      claim.hangUp(type, operatorId);
    } catch (DomainException e) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_STATUS_CONFLICT, e.getMessage());
    }
    save(claim);
  }

  /** 恢复挂起（FRAUD 仅风控岗：调用方已做角色校验）。翻译口径同 {@link #hangUp}。 */
  @Transactional
  public void resume(long claimId, String operatorId) {
    ClaimCase claim = loadForUpdate(claimId);
    try {
      claim.resume(operatorId);
    } catch (DomainException e) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_STATUS_CONFLICT, e.getMessage());
    }
    save(claim);
  }

  /** 超期关单（21 → 45；schedule 扫描触发）。 */
  @Transactional
  public void closeForStale(long claimId, String operatorId) {
    ClaimCase claim = loadForUpdate(claimId);
    claim.closeForStale(operatorId);
    save(claim);
  }

  // ===================== 读操作 =====================

  /** 赔案详情（头 + 明细扁平投影，按明细行组装）。 */
  @Transactional(readOnly = true)
  public ClaimWithItemsProjection detail(long claimId) {
    List<ClaimWithItemsProjection> rows = claimCaseRepository.findClaimWithItems(claimId);
    if (rows.isEmpty()) {
      throw ClaimErrors.supplier(ClaimErrorCodes.CLAIM_NOT_FOUND, claimId).get();
    }
    return rows.get(0);
  }

  /** 待办/列表分页（工作台）。 */
  @Transactional(readOnly = true)
  public PageResult<ClaimHeadProjection> page(
      List<ClaimStatus> statuses, String sourceChannel, int page, int size) {
    long tenantId = tenantPort.currentTenantId();
    List<ClaimStatus> effective =
        statuses == null || statuses.isEmpty() ? List.of(ClaimStatus.PENDING_ADJUST) : statuses;
    return claimCaseRepository.findClaimPage(tenantId, effective, sourceChannel, page, size);
  }

  /**
   * 案件详情（赔案头 + 费用明细，§7.2 员工端进度追踪 / 工作台查看详情）。
   *
   * <p>SQL 已带租户锚点（AUTO 模式 SDK 注入 {@code c.tenant_id}），查不到即 404—— 与 {@link #loadForUpdate} 的"租户不符也报
   * 404"同口径，避免借详情探测他租户案件存在性（§11.3 防枚举）。明细经投影获取，不经聚合内集合（SDK {@code findById} 不回填集合）。
   */
  @Transactional(readOnly = true)
  public List<ClaimWithItemsProjection> getClaimWithItems(long claimId) {
    List<ClaimWithItemsProjection> rows = claimCaseRepository.findClaimWithItems(claimId);
    if (rows == null || rows.isEmpty()) {
      throw ClaimErrors.supplier(ClaimErrorCodes.CLAIM_NOT_FOUND, claimId).get();
    }
    return rows;
  }

  // ===================== 内部 =====================

  /** 加载聚合（租户内可见，404 语义统一）。 */
  private ClaimCase loadForUpdate(long claimId) {
    long tenantId = tenantPort.currentTenantId();
    ClaimCase claim = claimCaseRepository.findById(claimId);
    if (claim == null || !Long.valueOf(tenantId).equals(claim.getTenantId())) {
      throw ClaimErrors.supplier(ClaimErrorCodes.CLAIM_NOT_FOUND, claimId).get();
    }
    return claim;
  }

  /**
   * 初审核定金额按明细分摊回填 {@code valid_amount}（理算基准的数据来源）。
   *
   * <p>为什么在应用层而不是聚合：聚合 {@code findById} 不回填明细（读侧投影约定），聚合内拿不到明细行。 分摊用**累计比例法**：每行目标 = round(核定 ×
   * 累计金额 / 总金额)，行份额 = 相邻累计差—— 最后一行吸收全部舍入误差，保证 Σ valid == min(核定, Σ金额) 恒成立（不丢也不超），且单行不超过明细金额。
   */
  private void backfillItemValidAmounts(long claimId, BigDecimal approvedAmount) {
    if (approvedAmount == null || approvedAmount.signum() <= 0) {
      return; // 未核定/零核定：明细保持初始 0，理算按零赔付归因，不越权替审核员定价
    }
    List<ClaimItem> items = claimItemRepository.findByClaimId(claimId);
    if (items.isEmpty()) {
      return;
    }
    BigDecimal total = BigDecimal.ZERO;
    for (ClaimItem item : items) {
      total = total.add(item.getAmount());
    }
    if (total.signum() <= 0) {
      return;
    }
    BigDecimal approved = approvedAmount.min(total);
    BigDecimal amountCum = BigDecimal.ZERO;
    BigDecimal assignedCum = BigDecimal.ZERO;
    for (int i = 0; i < items.size(); i++) {
      ClaimItem item = items.get(i);
      amountCum = amountCum.add(item.getAmount());
      boolean last = i == items.size() - 1;
      BigDecimal targetCum =
          last
              ? approved
              : approved.multiply(amountCum).divide(total, 2, java.math.RoundingMode.HALF_UP);
      BigDecimal share = targetCum.subtract(assignedCum);
      if (share.signum() < 0) {
        share = BigDecimal.ZERO; // 前序四舍五入超拨时兜底，最后一行吸收
      }
      if (share.compareTo(item.getAmount()) > 0) {
        share = item.getAmount();
      }
      item.approveValidAmount(share);
      assignedCum = assignedCum.add(share);
      claimItemRepository.save(item);
    }
  }

  /** 供其他应用服务编排案件状态流转（如申诉）加载聚合。 */
  public ClaimCase getClaimAggregate(long claimId) {
    return loadForUpdate(claimId);
  }

  /** 供其他应用服务持久化案件聚合（含乐观锁重试 + 事件发布）。 */
  public void saveClaim(ClaimCase claim) {
    save(claim);
  }

  /**
   * 保存 + 发布领域事件；乐观锁冲突重试 ≤3 次（指数退避 50ms 起），超限转 409。
   *
   * <p>重试必须重新加载聚合：旧内存实例的 version 已过期，直接重试 save 必然再次冲突。
   */
  private void save(ClaimCase claim) {
    for (int attempt = 1; ; attempt++) {
      try {
        claimCaseRepository.save(claim);
        domainEventPublisher.publishFrom(claim);
        return;
      } catch (OptimisticLockingFailureException ex) {
        if (attempt >= MAX_RETRY) {
          throw ClaimErrors.of(ClaimErrorCodes.CLAIM_CONCURRENT_CONFLICT, claim.getId(), ex);
        }
        ClaimCase fresh = claimCaseRepository.findById(claim.getId());
        if (fresh == null) {
          throw ClaimErrors.supplier(ClaimErrorCodes.CLAIM_NOT_FOUND, claim.getId()).get();
        }
        claim = fresh;
        try {
          Thread.sleep(50L * attempt);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw new DomainException("重试被中断", ie);
        }
      }
    }
  }

  private static final int MAX_RETRY = 3;

  /** 报案提交命令（应用层入站契约，平铺于 {@code application/}，无 handler 子包——ADR-0028）。 */
  public record SubmitClaim(
      String idempotencyKey,
      String policyNo,
      Long planId,
      String insuredIdHash,
      String claimType,
      String invoiceCode,
      String invoiceNo,
      java.time.LocalDate invoiceDate,
      String hospitalCode,
      String diagnosisCode,
      java.time.Instant outInsureTime,
      String batchNo,
      List<ClaimItemDto> items) {

    /**
     * 测试/控制器辅助：注入幂等键（record wither）。
     *
     * <p>幂等键来自 {@code Idempotency-Key} 请求头而非请求体（MapStruct 从体映射，该字段恒为 null）， 控制器在 {@code
     * toSubmitClaim} 后调用本方法把 header 值补进命令——域层查重 （{@code findByIdempotencyKey}）与 {@code
     * t_claim_case.idempotency_key} NOT NULL 列都依赖它。
     */
    public SubmitClaim withIdempotencyKey(String idempotencyKey) {
      return new SubmitClaim(
          idempotencyKey,
          policyNo,
          planId,
          insuredIdHash,
          claimType,
          invoiceCode,
          invoiceNo,
          invoiceDate,
          hospitalCode,
          diagnosisCode,
          outInsureTime,
          batchNo,
          items);
    }

    /** 测试/域内辅助：替换出险时间（record wither）。 */
    public SubmitClaim withOutInsureTime(java.time.Instant outInsureTime) {
      return new SubmitClaim(
          idempotencyKey,
          policyNo,
          planId,
          insuredIdHash,
          claimType,
          invoiceCode,
          invoiceNo,
          invoiceDate,
          hospitalCode,
          diagnosisCode,
          outInsureTime,
          batchNo,
          items);
    }

    /** 测试/域内辅助：替换被保人假名化 ID（record wither）。 */
    public SubmitClaim withInsuredIdHash(String insuredIdHash) {
      return new SubmitClaim(
          idempotencyKey,
          policyNo,
          planId,
          insuredIdHash,
          claimType,
          invoiceCode,
          invoiceNo,
          invoiceDate,
          hospitalCode,
          diagnosisCode,
          outInsureTime,
          batchNo,
          items);
    }

    /** 明细入参（协议层透传；服务端对医保三段拆分字段拥有最终解释权）。 */
    public record ClaimItemDto(
        String itemName,
        String itemCode,
        com.bone.claim.domain.model.claim.valueobject.ItemCategory category,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount,
        String department) {}
  }

  /** 状态跃迁事件类型再导出（供订阅方引用，避免直接 import domain.event 包）。 */
  public static Class<ClaimStatusChangedEvent> statusChangedEventType() {
    return ClaimStatusChangedEvent.class;
  }
}
