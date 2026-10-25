package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.projection.ClaimHeadProjection;
import com.bone.claim.domain.model.claim.projection.ClaimTenantRef;
import com.bone.claim.domain.model.claim.projection.ClaimWithItemsProjection;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.util.List;

/**
 * 赔案仓储（ADR-0030：写侧与本聚合读模型合并为单一仓储）。继承 SDK {@link Repository}，由 {@code @EnableSqlRepositories} 代理实现。
 *
 * <p>写走 SDK 原生 {@code save/update}（{@code @Version} 乐观锁）；读返回领域投影。自定义 SQL 模板固定放 {@code
 * resources/sql/…/ClaimCaseRepository/<方法名>.sql}（禁 @Sql 内联影子 SQL）；Criteria 查询走 {@code default} 方法。
 */
public interface ClaimCaseRepository extends Repository<ClaimCase, Long> {

  /**
   * 赔案头 + 明细扁平投影（每明细一行，头字段逐行重复）。
   *
   * <p>AUTO 租户：SQL 模板放 {@code /*bone:tenant*}{@code /} 锚点，SDK 自动注入 {@code c.tenant_id =
   * :__boneTenantId__}。
   */
  @TenantScope(value = TenantScopeMode.AUTO, column = "c.tenant_id")
  List<ClaimWithItemsProjection> findClaimWithItems(@Param("claimId") long claimId);

  /**
   * 超期未补件关单扫描（全租户，定时任务专用）：{@code status = 21} 且 {@code updated_at < before}。
   *
   * <p>仅 {@code adapter.schedule} 可调用（ArchitectureTest {@code
   * schedule_only_calls_all_tenants_repository_methods} 收紧）。
   */
  @TenantScope(TenantScopeMode.ALL)
  List<ClaimTenantRef> findStaleEvidenceClaimsAllTenants(
      @Param("before") java.sql.Timestamp before);

  /**
   * 后督抽样候选（全租户，定时任务专用）：{@code status = FINISHED}（已自动结案），按创建时间倒序取前 {@code limit} 条。
   *
   * <p>仅 {@code adapter.schedule} 可调用（ArchitectureTest {@code
   * schedule_only_calls_all_tenants_repository_methods} 收紧）；分层抽样比例细化属后续迭代。
   */
  @TenantScope(TenantScopeMode.ALL)
  List<ClaimTenantRef> findFinishedForInspectionAllTenants(@Param("limit") int limit);

  /**
   * 未终结案件扫描（全租户，SLA 物化 {@code RefreshLimitHourJob} 专用）：{@code status} 非终态（≠ CLOSED_REJECTED /
   * FINISHED），按 id 升序取一批。
   *
   * <p>仅 {@code adapter.schedule} 可调用（ArchitectureTest 收紧）；逐笔由 Job 用 {@code runAs} 显式携带租户。
   */
  @TenantScope(TenantScopeMode.ALL)
  List<ClaimTenantRef> findActiveClaimsAllTenants(@Param("limit") int limit);

  /** 按报案号查（对外检索键）。 */
  default ClaimCase findByClaimNo(Long tenantId, String claimNo) {
    Criteria<ClaimCase> criteria =
        Criteria.<ClaimCase>create()
            .eq(ClaimCase::getTenantId, tenantId)
            .eq(ClaimCase::getClaimNo, claimNo);
    return findOneByCriteria(criteria);
  }

  /** 按幂等键查（重复提交 → 返回首次结果，非 409）。 */
  default ClaimCase findByIdempotencyKey(Long tenantId, String idempotencyKey) {
    Criteria<ClaimCase> criteria =
        Criteria.<ClaimCase>create()
            .eq(ClaimCase::getTenantId, tenantId)
            .eq(ClaimCase::getIdempotencyKey, idempotencyKey);
    return findOneByCriteria(criteria);
  }

  /** 待办分页（工作台）：按状态集 + 可选挂起/渠道过滤，priority 排序由读侧投影组装。 */
  default PageResult<ClaimHeadProjection> findClaimPage(
      Long tenantId, List<ClaimStatus> statuses, String sourceChannel, int page, int size) {
    // DB 存枚举 name，查询值须转 name（SDK String 持久化口径）
    List<String> statusNames =
        statuses.stream()
            .map(com.bone.claim.domain.model.claim.valueobject.ClaimStatus::name)
            .toList();
    Criteria<ClaimCase> criteria =
        Criteria.<ClaimCase>create()
            .eq(ClaimCase::getTenantId, tenantId)
            .in(!statusNames.isEmpty(), ClaimCase::getStatus, statusNames.toArray())
            .eq(
                sourceChannel != null && !sourceChannel.isBlank(),
                ClaimCase::getSourceChannel,
                sourceChannel)
            .orderByDesc(ClaimCase::getCreatedAt)
            .page(page, size);
    return pageByCriteria(criteria).map(ClaimHeadProjection::from);
  }
}
