package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.sign.ClaimSignBatch;
import com.bone.claim.domain.model.sign.SignBatchItem;
import com.bone.claim.domain.model.sign.SignTrackLog;
import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.time.Instant;
import java.util.List;

/**
 * 签收批次仓储。save/insert 由 SDK 按 @Version 处理；CAS 条件更新为自定义 SQL。 方法名避免
 * insert/update/delete/persist/flush/merge/exists 保留词。
 */
public interface ClaimSignBatchRepository extends Repository<ClaimSignBatch, Long> {

  default ClaimSignBatch findByBatchNo(Long tenantId, String batchNo) {
    return findOneByCriteria(
        Criteria.<ClaimSignBatch>create()
            .eq(ClaimSignBatch::getTenantId, tenantId)
            .eq(ClaimSignBatch::getBatchNo, batchNo));
  }

  default ClaimSignBatch findByBusinessKey(Long tenantId, String businessKey) {
    return findOneByCriteria(
        Criteria.<ClaimSignBatch>create()
            .eq(ClaimSignBatch::getTenantId, tenantId)
            .eq(ClaimSignBatch::getBusinessKey, businessKey));
  }

  /** 批次明细（自动建案输入），按 id 顺序稳定处理。 */
  @TenantScope(value = TenantScopeMode.AUTO, column = "i.tenant_id")
  List<SignBatchItem> findItemsByBatchId(@Param("batchId") long batchId);

  /** 明细关联案件（新建或幂等命中），只改关联与结果状态。 */
  @TenantScope(value = TenantScopeMode.AUTO, column = "i.tenant_id")
  int linkCaseForItem(
      @Param("id") long id,
      @Param("tenantId") long tenantId,
      @Param("claimId") long claimId,
      @Param("status") String status);

  /** 明细结果状态推进（跳过/待人工/失败）。 */
  @TenantScope(value = TenantScopeMode.AUTO, column = "i.tenant_id")
  int markCaseForItem(
      @Param("id") long id, @Param("tenantId") long tenantId, @Param("status") String status);

  /** 确认签收 CAS：仅当前置态匹配时置 FINISHED，返回影响行数（0 即冲突/已签收）。 */
  @TenantScope(value = TenantScopeMode.AUTO, column = "b.tenant_id")
  int confirmWithCas(
      @Param("id") long id,
      @Param("tenantId") long tenantId,
      @Param("expectedStatus") String expectedStatus,
      @Param("operator") String operator,
      @Param("remark") String remark);

  /** 签收批次分页查询（服务端真分页，条件对齐普康 TPA 签收台）。 */
  default PageResult<ClaimSignBatch> findBatchPage(
      Long tenantId,
      String source,
      String batchNo,
      String signType,
      String signChannel,
      String signStatus,
      String insuranceCompany,
      String insuranceSubsidiary,
      String insuringAgency,
      String signOperator,
      Instant signTimeStart,
      Instant signTimeEnd,
      Instant createTimeStart,
      Instant createTimeEnd,
      int page,
      int size) {
    Criteria<ClaimSignBatch> criteria =
        Criteria.<ClaimSignBatch>create().eq(ClaimSignBatch::getTenantId, tenantId);
    if (present(source) && !"ALL".equalsIgnoreCase(source)) {
      criteria.eq(ClaimSignBatch::getSource, source);
    }
    criteria.eq(present(signType), ClaimSignBatch::getSignType, signType);
    criteria.eq(present(signStatus), ClaimSignBatch::getSignStatus, signStatus);
    criteria.likeIfPresent(ClaimSignBatch::getBatchNo, batchNo);
    criteria.likeIfPresent(ClaimSignBatch::getSignChannel, signChannel);
    criteria.likeIfPresent(ClaimSignBatch::getInsuranceCompany, insuranceCompany);
    criteria.likeIfPresent(ClaimSignBatch::getInsuranceSubsidiary, insuranceSubsidiary);
    criteria.likeIfPresent(ClaimSignBatch::getInsuringAgency, insuringAgency);
    criteria.likeIfPresent(ClaimSignBatch::getSignOperator, signOperator);
    criteria.gte(signTimeStart != null, ClaimSignBatch::getSignTime, signTimeStart);
    criteria.lte(signTimeEnd != null, ClaimSignBatch::getSignTime, signTimeEnd);
    criteria.gte(createTimeStart != null, ClaimSignBatch::getCreatedAt, createTimeStart);
    criteria.lte(createTimeEnd != null, ClaimSignBatch::getCreatedAt, createTimeEnd);
    return pageByCriteria(criteria.page(page, size));
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }

  /** 追加签收轨迹（append-only）。INSERT 显式写 tenant_id 故用 MANUAL；SDK 要求 SQL 方法声明策略。 */
  @TenantScope(value = TenantScopeMode.MANUAL)
  void saveTrack(
      @Param("id") long id,
      @Param("tenantId") long tenantId,
      @Param("batchId") long batchId,
      @Param("claimId") Long claimId,
      @Param("signStatus") String signStatus,
      @Param("actionType") String actionType,
      @Param("message") String message,
      @Param("remark") String remark,
      @Param("operator") String operator,
      @Param("occurredAt") Instant occurredAt);

  /** 批次轨迹（append-only），按时间升序；自定义 SQL，首行 SELECT。 */
  @TenantScope(value = TenantScopeMode.AUTO, column = "t.tenant_id")
  List<SignTrackLog> findTracksByBatchId(@Param("batchId") long batchId);
}
