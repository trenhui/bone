package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimItem;
import com.bone.claim.domain.model.claim.ClaimReport;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimReportRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.util.DistributedIdGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 报案应用门面（1:N 的"一"）：登记报案、按报案分赔案、列报案下赔案。
 *
 * <p>报案号（RP）与赔案号（CL）分工：报案是事故通知事件，赔案是赔付处理单元。一个报案下多笔赔案各自独立理算/结案，
 * 但共享事故主体上下文（保单/被保人/出险时间）。资金与额度幂等键始终以赔案号为单位，分案不会踩踏。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ClaimReportApplicationService {

  private final ClaimReportRepository claimReportRepository;
  private final ClaimCaseRepository claimCaseRepository;
  private final DomainEventPublisher domainEventPublisher;
  private final TenantPort tenantPort;

  /** 登记报案结果。 */
  public record RegisteredReport(Long reportId, String reportNo) {}

  /** 报案下单个赔案的精简投影（列表展示，不下钻明细）。 */
  public record CaseUnderReport(
      Long claimId, String claimNo, String status, String externalStatus, BigDecimal claimAmount) {}

  /** 分案命令（子案明细 + 发票上下文；主体/保单/出险时间继承母案）。 */
  public record SplitCase(
      String reportNo,
      String idempotencyKey,
      String invoiceCode,
      String invoiceNo,
      java.time.LocalDate invoiceDate,
      String hospitalCode,
      String diagnosisCode,
      List<ClaimItemDto> items) {

    /** 子案费用明细入参。 */
    public record ClaimItemDto(
        String itemName,
        String itemCode,
        com.bone.claim.domain.model.claim.valueobject.ItemCategory category,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount,
        String department,
        String feeCode) {}
  }

  /** 登记报案（幂等：同租户同幂等键返回首报案）。 */
  @Transactional
  public RegisteredReport register(RegisterReport command) {
    return doRegister(command);
  }

  /**
   * 由提交编排层调用的报案登记入口（与 {@link #register} 同语义，独立事务）。
   *
   * <p>单独命名是显式契约：用户提交主链路必须「先报案、后建案」，两段各自独立事务。「已报案未立案」是业界合法状态， 建案失败后遗留的报案无需补偿删除。
   */
  @Transactional
  public RegisteredReport registerFromSubmission(RegisterReport command) {
    return doRegister(command);
  }

  private RegisteredReport doRegister(RegisterReport command) {
    long tenantId = tenantPort.currentTenantId();
    ClaimReport existing =
        claimReportRepository.findByIdempotencyKey(tenantId, command.idempotencyKey());
    if (existing != null) {
      return new RegisteredReport(existing.getId(), existing.getReportNo());
    }
    long reportId = DistributedIdGenerator.generateLongId();
    ClaimReport report =
        ClaimReport.register(
            reportId,
            tenantId,
            command.idempotencyKey(),
            command.policyNo(),
            command.insuredIdHash(),
            command.outInsureTime(),
            command.lossDescription(),
            command.sourceChannel(),
            command.sourceSystem());
    claimReportRepository.insert(report);
    return new RegisteredReport(report.getId(), report.getReportNo());
  }

  /** 登记报案命令（租户由 TenantPort 从上下文取，防止外部越权指定）。 */
  public record RegisterReport(
      String idempotencyKey,
      String policyNo,
      String insuredIdHash,
      Instant outInsureTime,
      String lossDescription,
      String sourceChannel,
      String sourceSystem) {}

  /**
   * 在报案下新建一个赔案（分案）。
   *
   * <p>租户隔离：按 reportNo + tenantId 定位报案取不到即 404（防枚举）；CLOSED 态 409。 子案赔案号由新雪花 ID 派生，
   * 与母案不同；主体/保单/出险时间/报案归属继承母案，明细与发票来自命令；理算/结算/审核结果不克隆。
   */
  @Transactional
  public RegisteredCase splitCase(SplitCase command, String idempotencyKey) {
    long tenantId = tenantPort.currentTenantId();
    ClaimReport report = claimReportRepository.findByReportNo(tenantId, command.reportNo());
    if (report == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_REPORT_NOT_FOUND, command.reportNo());
    }
    if (!report.canSplit()) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_REPORT_CLOSED, command.reportNo());
    }
    List<ClaimCase> cases = claimCaseRepository.findByReportId(tenantId, report.getId());
    if (cases.isEmpty()) {
      // 报案存在却没有首案，属数据不一致；用一个明确的 404 而不是 IndexOutOfBounds
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_REPORT_NOT_FOUND, command.reportNo() + " (no case)");
    }
    ClaimCase mother = cases.get(0);

    long childId = DistributedIdGenerator.generateLongId();
    String childKey =
        idempotencyKey == null || idempotencyKey.isBlank()
            ? command.idempotencyKey()
            : idempotencyKey;
    List<ClaimItem> items =
        command.items().stream()
            .map(
                dto -> {
                  ClaimItem item =
                      ClaimItem.of(
                          DistributedIdGenerator.generateLongId(),
                          tenantId,
                          childId,
                          dto.itemName(),
                          dto.itemCode(),
                          dto.category(),
                          dto.quantity(),
                          dto.unitPrice(),
                          dto.amount());
                  item.assignDepartment(dto.department());
                  item.assignFeeCode(dto.feeCode());
                  return item;
                })
            .toList();

    ClaimCase child =
        ClaimCase.splitFrom(
            mother,
            childId,
            childKey,
            command.invoiceCode(),
            command.invoiceNo(),
            command.invoiceDate(),
            command.hospitalCode(),
            command.diagnosisCode(),
            items);
    // 分案即受理（与首案一致：员工端提交 = 已受理）。
    child.accept();
    claimCaseRepository.insert(child);
    domainEventPublisher.publishFrom(child);
    // 报案 case_count 不计入本事务：分案只持久化 ClaimCase 聚合（R9 一事务一聚合）。
    // 计数由编排层在子案提交后调用 #recordCaseCreation 以独立事务登记。
    return new RegisteredCase(child.getId(), child.getClaimNo());
  }

  /**
   * 记录一次建案到报案计数（分案后由编排层以独立事务调用）。
   *
   * <p>拆成独立事务是 R9 的要求：{@link #splitCase} 只写 ClaimCase 聚合，本方法只写 ClaimReport 聚合。
   * 失败仅影响计数（可对账修复），不回滚已成立的赔案——这与「报案是事件、赔案是处理单元」的一致性边界一致。
   */
  @Transactional
  public void recordCaseCreation(String reportNo) {
    long tenantId = tenantPort.currentTenantId();
    ClaimReport report = claimReportRepository.findByReportNo(tenantId, reportNo);
    if (report == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_REPORT_NOT_FOUND, reportNo);
    }
    report.onCaseCreated();
    claimReportRepository.update(report);
  }

  /** 分案结果。 */
  public record RegisteredCase(Long claimId, String claimNo) {}

  /** 列出报案下全部赔案（时间升序，母案在前）。 */
  @Transactional(readOnly = true)
  public List<CaseUnderReport> listCases(String reportNo) {
    long tenantId = tenantPort.currentTenantId();
    ClaimReport report = claimReportRepository.findByReportNo(tenantId, reportNo);
    if (report == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_REPORT_NOT_FOUND, reportNo);
    }
    return claimCaseRepository.findByReportId(tenantId, report.getId()).stream()
        .map(
            c ->
                new CaseUnderReport(
                    c.getId(),
                    c.getClaimNo(),
                    c.status().name(),
                    c.externalStatus(),
                    c.claimAmount()))
        .toList();
  }

  /** 关闭报案（不再接受新赔案）。幂等：已关闭返回 false。 */
  @Transactional
  public boolean closeReport(String reportNo) {
    long tenantId = tenantPort.currentTenantId();
    ClaimReport report = claimReportRepository.findByReportNo(tenantId, reportNo);
    if (report == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_REPORT_NOT_FOUND, reportNo);
    }
    boolean changed = report.close();
    if (changed) {
      claimReportRepository.update(report);
    }
    return changed;
  }
}
