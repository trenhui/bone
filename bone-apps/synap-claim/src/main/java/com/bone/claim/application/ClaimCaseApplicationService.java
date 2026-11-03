package com.bone.claim.application;

import com.bone.claim.application.port.out.PolicyQueryPort;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.common.PageBounds;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimItem;
import com.bone.claim.domain.model.claim.event.ClaimStatusChangedEvent;
import com.bone.claim.domain.model.claim.projection.ClaimHeadProjection;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.claim.domain.model.claim.valueobject.Decision;
import com.bone.claim.domain.model.claim.valueobject.HangUpType;
import com.bone.claim.domain.model.mapping.MappingMissResolvedEvent;
import com.bone.claim.domain.model.mapping.MasterDataDomain;
import com.bone.claim.domain.model.mapping.ResolutionStatus;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimInsurerRepository;
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
  private final CodeMappingApplicationService codeMappingApplicationService;
  private final ClaimInsurerRepository claimInsurerRepository;

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
    if (!snapshot.covers(command.insuredIdHash())) {
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
                  // 费用类别是责任费用规则的判定维度：随明细落库，缺失则引擎按责任统一比例算并落诊断。
                  item.assignFeeCode(dto.feeCode());
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
    // 多保司映射解析（主设计 §6.5，P0 判据①-④）：raw 码 → 标准码，落双码 + 版本号。
    // 只对「已登记承保主体」的报案生效（接一家配一家）；未带 insurerCode 或保司未登记
    // 的报案走存量行为（零变化），映射面灰度推进。
    boolean codeResolutionPending = resolveSubmissionCodes(command, claim, items);
    // PIPL 单独同意留痕（§9.5 / DSAR G10）：命令带记录号才落库，不带则保持 NULL。
    // 只写不校验格式——记录号的形状由同意服务定义；空值不拒收（存量渠道/旁路同步尚无此字段，
    // 一刀切 fail-closed 会让存量报案全线 400），真正的闸门在撤回侧。
    if (command.consentRecordId() != null && !command.consentRecordId().isBlank()) {
      claim.recordConsent(command.consentRecordId());
    }
    // 报案即受理签收（员工端提交 = 已受理；缺件走后续 requestEvidence，不阻塞受理）
    claim.accept();
    // 解析有未命中（判据②「收单转人工且写 miss」）：挂起阻断自动推进——挂起是与状态码
    // 正交的机制（不改状态），映射解决后由重解析自动恢复。fail-closed 的落点是"阻断自动
    // 理算"，而不是"拒收报案"——拒收会让渠道重推，且保司码值问题不该由报案人承担。
    if (codeResolutionPending) {
      claim.hangUp(HangUpType.EVIDENCE, "SYSTEM");
    }

    claimCaseRepository.insert(claim);
    domainEventPublisher.publishFrom(claim);
    return new SubmittedClaim(claim.getId(), claim.getClaimNo());
  }

  /**
   * 收单码解析（明细 FEE_CATEGORY + 案件 HOSPITAL/DISEASE 三域；§6.5）。
   *
   * <p><b>解析与落库的分工</b>：{@link CodeMappingApplicationService#resolve} 负责「查映射 + MISS
   * 入队」（同一事务），本方法负责「把结果落到聚合」（{@code std_*} 列 + 版本号）。 MISSED 时<b>不</b>把 raw 码写进 std 列（判据④：空串 =
   * 未解析，消费方按维度缺失 fail-closed）， 但案件必须挂起——放行会让"没配映射"静默变成"按缺维度兜底赔付"。
   *
   * <p>逐域独立：医院命中而费用类别未命中时，医院的标准码照常回填（部分正确信息不因 其他域失败而丢弃）；整体 pending 状态由「任一域未命中」决定。
   *
   * @return true = 存在未解析成功的码（调用方须挂起案件）
   */
  private boolean resolveSubmissionCodes(
      SubmitClaim command, ClaimCase claim, List<ClaimItem> items) {
    String insurerCode = command.insurerCode() == null ? "" : command.insurerCode().trim();
    if (insurerCode.isEmpty()) {
      return false;
    }
    long tenantId = claim.getTenantId();
    // 保司未登记 = 该报案来自未接映射面的渠道/主体：跳过解析（存量行为），不挂起。
    if (claimInsurerRepository.findByInsurerCode(tenantId, insurerCode) == null) {
      return false;
    }
    boolean pending = false;
    String caseNo = claim.getClaimNo();
    // 案件级两域：医院 / 诊断
    String stdHospital = null;
    String stdDiagnosis = null;
    String caseMappingVersion = "";
    if (!claim.getHospitalCode().isBlank()) {
      CodeMappingApplicationService.ResolveView hospital =
          codeMappingApplicationService.resolve(
              new CodeMappingApplicationService.ResolveCommand(
                  insurerCode,
                  MasterDataDomain.HOSPITAL.code(),
                  claim.getHospitalCode(),
                  null,
                  null,
                  caseNo));
      if (hospital.status().equals(ResolutionStatus.RESOLVED.name())) {
        stdHospital = hospital.standardCode();
        caseMappingVersion = hospital.mappingVersion();
      } else {
        pending = true;
      }
    }
    if (!claim.getDiagnosisCode().isBlank()) {
      CodeMappingApplicationService.ResolveView diagnosis =
          codeMappingApplicationService.resolve(
              new CodeMappingApplicationService.ResolveCommand(
                  insurerCode,
                  MasterDataDomain.DISEASE.code(),
                  claim.getDiagnosisCode(),
                  null,
                  null,
                  caseNo));
      if (diagnosis.status().equals(ResolutionStatus.RESOLVED.name())) {
        stdDiagnosis = diagnosis.standardCode();
        if (caseMappingVersion.isEmpty()) {
          caseMappingVersion = diagnosis.mappingVersion();
        }
      } else {
        pending = true;
      }
    }
    if (stdHospital != null || stdDiagnosis != null) {
      claim.assignStdCodes(stdHospital, stdDiagnosis, caseMappingVersion);
    }
    // 明细级：费用类别（FEE_CATEGORY）
    for (ClaimItem item : items) {
      if (item.getFeeCode() == null || item.getFeeCode().isBlank()) {
        continue;
      }
      CodeMappingApplicationService.ResolveView fee =
          codeMappingApplicationService.resolve(
              new CodeMappingApplicationService.ResolveCommand(
                  insurerCode,
                  MasterDataDomain.FEE_CATEGORY.code(),
                  item.getFeeCode(),
                  null,
                  null,
                  caseNo));
      if (fee.status().equals(ResolutionStatus.RESOLVED.name())) {
        item.assignStdFeeCode(fee.standardCode(), fee.mappingVersion());
      } else {
        pending = true;
      }
    }
    return pending;
  }

  /** 分配初审（7 → 10）。 */
  @Transactional
  public void allocate(long claimId, String operatorId) {
    ClaimCase claim = loadForProcessing(claimId);
    claim.allocateToPreExam(operatorId);
    save(claim);
  }

  /** 开始初审（10 → 11/13）。 */
  @Transactional
  public void startPreExam(long claimId, boolean automated, String operatorId) {
    ClaimCase claim = loadForProcessing(claimId);
    claim.startPreExam(automated, operatorId);
    save(claim);
  }

  /** 决策图完成（11/13 → 12，回填理算核定金额）。 */
  @Transactional
  public void completePreExam(long claimId, BigDecimal approvedAmount, String operatorId) {
    ClaimCase claim = loadForProcessing(claimId);
    claim.completePreExam(operatorId, approvedAmount);
    save(claim);
    backfillItemValidAmounts(claimId, approvedAmount);
  }

  /** 转人工核赔（12 → 40）。 */
  @Transactional
  public void escalate(long claimId, String operatorId) {
    ClaimCase claim = loadForProcessing(claimId);
    claim.escalateToAdjust(operatorId);
    save(claim);
  }

  /** 领取核赔任务（40 → 41）。 */
  @Transactional
  public void claimAdjustTask(long claimId, String reviewerId) {
    ClaimCase claim = loadForProcessing(claimId);
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
    ClaimCase claim = loadForProcessing(claimId);
    claim.decide(decision, reviewerId, comment, rejectType, approvedAmount);
    save(claim);
    if (decision == Decision.APPROVED) {
      backfillItemValidAmounts(claimId, claim.approvedAmount());
    }
  }

  /** 发起双人复核（42 → 44；≥1 万）。 */
  @Transactional
  public void submitForSecondReview(long claimId, String reviewerId) {
    ClaimCase claim = loadForProcessing(claimId);
    claim.submitForSecondReview(reviewerId);
    save(claim);
  }

  /** 复核结论（44 → 50/45）。 */
  @Transactional
  public void completeSecondReview(long claimId, boolean approved, String reviewerId) {
    ClaimCase claim = loadForProcessing(claimId);
    claim.completeSecondReview(approved, reviewerId);
    save(claim);
  }

  /** 自动通过（12 → 50；R-FAST-01 快赔路径）。 */
  @Transactional
  public void autoApprove(long claimId, String operatorId) {
    ClaimCase claim = loadForProcessing(claimId);
    claim.autoApprove(operatorId);
    save(claim);
  }

  /** 缺件补录（→ 21）。 */
  @Transactional
  public void requestEvidence(long claimId, String operatorId) {
    ClaimCase claim = loadForProcessing(claimId);
    claim.requestEvidence(operatorId);
    save(claim);
  }

  /** 补录完成回流（21 → 10）。 */
  @Transactional
  public void evidenceSubmitted(long claimId, String operatorId) {
    ClaimCase claim = loadForProcessing(claimId);
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

  /**
   * 撤回同意（PIPL 第 15 条；DSAR 册 {@code POST /consent/revoke}）。
   *
   * <p><b>幂等且不报错</b>：数据主体重复撤回不是错误，返回 {@code false} 让调用方知道"本来就已撤回"。 已在撤回态时<b>不落库</b>——避免用一次空的事务去
   * bump {@code version}， 把并发中的核赔动作无谓地打成乐观锁冲突。
   *
   * <p><b>为什么不在本方法里做擦除</b>：擦除是跨表去标识化 + 归档密文块销毁（DSAR 册 §三），属异步作业，
   * 触发条件就是本方法置的标记；塞进请求事务会让一次撤回拖住整条资金链路。
   */
  @Transactional
  public boolean revokeConsent(long claimId) {
    ClaimCase claim = loadForUpdate(claimId);
    boolean changed = claim.withdrawConsent();
    if (changed) {
      save(claim);
    }
    return changed;
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
    // 调用方可能直接透传裸 @RequestParam（ReviewTaskController），收窄只能落在服务入口
    PageBounds bounds = PageBounds.of(page, size);
    return claimCaseRepository.findClaimPage(
        tenantId, effective, sourceChannel, bounds.page(), bounds.size());
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
  /**
   * 加载并校验「可继续处理」，供一切**处理类**用例使用（分配/初审/理算回填/核赔/复核/自动通过/补件）。
   *
   * <p><b>为什么闸门在应用层而不是聚合</b>：错误码与 HTTP 语义是应用层职责（域层只表达"不许"，不携带协议码—— 同 {@link #hangUp} 注释里那条教训）。聚合侧提供
   * {@link ClaimCase#consentWithdrawn()} 这个意图揭示查询， 判据只有一处定义，不出现"两处各判一遍"的漂移。
   *
   * <p><b>为什么关单/挂起/撤回本身不走这里</b>：撤回同意的语义是"停止<b>新的处理</b>"（PIPL 第 15 条），不是"把案子永久卡死"。
   * 超期关单、挂起/恢复属收尾与管理动作，若一并拦截，撤回反而会让案件既不能推进也不能结案—— 把合规要求执行成了运营事故。
   */
  private ClaimCase loadForProcessing(long claimId) {
    ClaimCase claim = loadForUpdate(claimId);
    if (claim.consentWithdrawn()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_CONSENT_WITHDRAWN, claim.getClaimNo());
    }
    return claim;
  }

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
      /**
       * PIPL 单独同意记录号（上游同意流水引用；空 = 该渠道未接同意流水，留痕字段保持 NULL）。
       *
       * <p>只透传、不校验格式：记录号的形状由同意服务定义，本模块无权假设。空值<b>不拒收</b>—— 存量渠道与旁路同步尚无此字段，一键 fail-closed 会让存量报案全线
       * 400；真正的合规闸门放在 <b>撤回</b>侧（撤回后拒绝继续处理），而不是"没填就拒收"。
       */
      String consentRecordId,
      /**
       * 承保主体编码（多保司映射的解析维度；空 = 该渠道未接映射链路，跳过解析走存量行为）。
       *
       * <p><b>为什么可空而不是必填</b>：映射面按保司逐步建设（接一家配一家），存量渠道与 旁路同步尚无此字段——一键 fail-closed 会让存量报案全线 400。已登记保司
       * + 命令带码 才触发解析；MISSING 才挂起 + 入队。P0 由报案渠道显式携带；待保单快照扩容 {@code bizIdentityCode}
       * 后切换为保单推导（届时本字段退化为校验位），见主设计 §6.5。
       */
      String insurerCode,
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
          consentRecordId,
          insurerCode,
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
          consentRecordId,
          insurerCode,
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
          consentRecordId,
          insurerCode,
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
        String department,
        /** 费用类别编码（t_ref_fee_category.fee_code；空 = 未分类，引擎按责任统一比例算并落诊断）。 */
        String feeCode) {}
  }

  /** 状态跃迁事件类型再导出（供订阅方引用，避免直接 import domain.event 包）。 */
  public static Class<ClaimStatusChangedEvent> statusChangedEventType() {
    return ClaimStatusChangedEvent.class;
  }

  // ===================== 未命中解决后的自动重解析（主设计 §14 判据⑨） =====================

  /**
   * 映射未命中解决后，重扫受影响的在办案件并回填标准码。
   *
   * <p><b>为什么挂在 AFTER_COMMIT 而不是 resolveMiss 同事务内</b>：重解析要读刚建立的映射，
   * 同事务内读得到（同连接可见未提交数据），但监听器若与发布方同事务，赔案侧的乐观锁冲突 会把「映射已解决」整体回滚——队列项已关、映射已建，只因顺带重扫失败而全部作废，
   * 运营得重做一遍。补偿动作独立成事务，失败可重试（下次 resolveMiss 或人工再触发）。
   *
   * <p><b>幂等</b>：扫描条件是 {@code std = ''}（未解析行），已解析行不被重扫覆盖—— 重复消费不重复落账；已结案案件不入扫描集（历史事实不改写）。
   *
   * <p><b>恢复口径</b>：案件因解析失败被挂起（EVIDENCE + SYSTEM），当其全部非空 raw 码 （案件级医院/诊断 +
   * 明细级费用类别）都有标准码时自动恢复；仅部分解决不恢复—— 半解释状态的案件放行进理算，等于把"没配完映射"伪装成"配置完整"。
   */
  @org.springframework.transaction.event.TransactionalEventListener(
      phase = org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  public void onMissResolved(MappingMissResolvedEvent event) {
    MasterDataDomain domain;
    try {
      domain = MasterDataDomain.fromCode(event.domain());
    } catch (IllegalArgumentException e) {
      return; // 非映射域（防御），跳过
    }
    switch (domain) {
      case FEE_CATEGORY -> rescanFeeCategory(
          event.tenantId(), event.insurerCode(), event.rawCode());
      case HOSPITAL -> rescanCaseLevel(
          event.tenantId(), event.insurerCode(), event.rawCode(), true);
      case DISEASE -> rescanCaseLevel(
          event.tenantId(), event.insurerCode(), event.rawCode(), false);
      default -> {
        /* 其余域暂无案件级落点（CERT_TYPE 案件不落），跳过 */
      }
    }
  }

  /** 明细级重扫：该原始费用类别码的全部未解析行。 */
  private void rescanFeeCategory(long tenantId, String insurerCode, String rawCode) {
    List<ClaimItem> pending = claimItemRepository.findPendingByFeeCode(tenantId, rawCode);
    for (ClaimItem item : pending) {
      CodeMappingApplicationService.ResolveView fee =
          codeMappingApplicationService.resolve(
              new CodeMappingApplicationService.ResolveCommand(
                  insurerCode, MasterDataDomain.FEE_CATEGORY.code(), rawCode, null, null, null));
      if (!fee.status().equals(ResolutionStatus.RESOLVED.name())) {
        continue; // 仍解析失败（如拆分父码）：留着下次，行保持未解析态
      }
      item.assignStdFeeCode(fee.standardCode(), fee.mappingVersion());
      claimItemRepository.save(item);
      tryResumeIfFullyResolved(claimCaseRepository.findById(item.getClaimId()));
    }
  }

  /** 案件级重扫：{@code isHospital=true} 扫医院域，否则诊断域。 */
  private void rescanCaseLevel(
      long tenantId, String insurerCode, String rawCode, boolean isHospital) {
    List<ClaimCase> pending =
        isHospital
            ? claimCaseRepository.findPendingByHospitalCode(tenantId, rawCode)
            : claimCaseRepository.findPendingByDiagnosisCode(tenantId, rawCode);
    for (ClaimCase claim : pending) {
      CodeMappingApplicationService.ResolveView view =
          codeMappingApplicationService.resolve(
              new CodeMappingApplicationService.ResolveCommand(
                  insurerCode,
                  (isHospital ? MasterDataDomain.HOSPITAL : MasterDataDomain.DISEASE).code(),
                  rawCode,
                  null,
                  null,
                  null));
      if (!view.status().equals(ResolutionStatus.RESOLVED.name())) {
        continue;
      }
      if (isHospital) {
        claim.assignStdCodes(view.standardCode(), null, view.mappingVersion());
      } else {
        claim.assignStdCodes(null, view.standardCode(), view.mappingVersion());
      }
      save(claim);
      tryResumeIfFullyResolved(claim);
    }
  }

  /** 全部码已解析则自动恢复挂起（仅 EVIDENCE + SYSTEM 挂起——人工挂的起因不是码， 自动恢复会越过当初挂起的人的判断；FRAUD 恢复有独立权限闸门，系统绝不代行）。 */
  private void tryResumeIfFullyResolved(ClaimCase claim) {
    if (claim == null
        || claim.getHangUpStatus() != 1
        || !HangUpType.EVIDENCE.name().equals(claim.getHangUpType())) {
      return;
    }
    boolean caseLevelComplete =
        (claim.getHospitalCode().isBlank() || !claim.getStdHospitalCode().isBlank())
            && (claim.getDiagnosisCode().isBlank() || !claim.getStdDiagnosisCode().isBlank());
    if (!caseLevelComplete) {
      return;
    }
    boolean itemsComplete =
        claimItemRepository.findByClaimId(claim.getId()).stream()
            .allMatch(
                i ->
                    i.getFeeCode() == null
                        || i.getFeeCode().isBlank()
                        || (i.getStdFeeCode() != null && !i.getStdFeeCode().isBlank()));
    if (itemsComplete) {
      claim.resume("SYSTEM");
      save(claim);
    }
  }
}
