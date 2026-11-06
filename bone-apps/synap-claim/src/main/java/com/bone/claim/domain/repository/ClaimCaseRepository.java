package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.projection.ClaimHeadProjection;
import com.bone.claim.domain.model.claim.projection.ClaimSettlementRef;
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
   * 跨案发票去重（R-FRAUD-07 的业务前置）：同租户下同一发票代码 + 号码对应的赔案。
   *
   * <p>显式带 tenantId 条件，不依赖 SDK 自动租户过滤；DB 唯一约束 {@code uk_tenant_invoice} 仍是并发下的最后防线。
   * 调用方须排除本案自身，用于「建单时才 409」提前为「材料阶段就拦截」。
   */
  default List<ClaimCase> findByInvoice(Long tenantId, String invoiceCode, String invoiceNo) {
    return findByCriteria(
        Criteria.<ClaimCase>create()
            .eq(ClaimCase::getTenantId, tenantId)
            .eq(ClaimCase::getInvoiceCode, invoiceCode)
            .eq(ClaimCase::getInvoiceNo, invoiceNo));
  }

  /**
   * 跨案历史取数：同租户下同一被保人假名化 ID 对应的全部赔案（任意状态）。
   *
   * <p>供 R-FRAUD-22 / R-ANOM-03（历史重复 / 近 90 天高频）判定；调用方须排除本案自身（{@code ctx.claimId()}）。
   * 否则新报案把自身算进历史、必然命中 R-FRAUD-22，造成 100% 误报。
   */
  default List<ClaimCase> findByInsuredIdHash(Long tenantId, String insuredIdHash) {
    return findByCriteria(
        Criteria.<ClaimCase>create()
            .eq(ClaimCase::getTenantId, tenantId)
            .eq(ClaimCase::getInsuredIdHash, insuredIdHash));
  }

  /**
   * 赔案头 + 明细扁平投影（每明细一行，头字段逐行重复）。
   *
   * <p>AUTO 租户：SQL 模板放 {@code /*bone:tenant*}{@code /} 锚点，SDK 自动注入 {@code c.tenant_id =
   * :__boneTenantId__}。
   */
  @TenantScope(value = TenantScopeMode.AUTO, column = "c.tenant_id")
  List<ClaimWithItemsProjection> findClaimWithItems(@Param("claimId") long claimId);

  /**
   * 超期未补件关单扫描（全租户，定时任务专用）：{@code status = EVIDENCE_SUBMITTING} 且 {@code updated_at < before}。
   *
   * <p>注意状态口径：{@code t_claim_case.status} 落的是枚举 <b>name</b>（{@code ClaimCase#transition} 写 {@code
   * to.name()}），不是 {@code ClaimStatus#code()} 的数字码——模板里写 {@code '21'} 会一行都匹配不到，定时任务静默空转。
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

  /**
   * 某业务日内结案（{@code status = FINISHED} 且 {@code closed_at} 落在 {@code [from, to)}）的赔案最小投影（对账
   * CASE_SETTLE 通道输入）。
   *
   * <p><b>为什么按 {@code closed_at} 而不是 {@code updated_at}</b>：后者任何字段修改都会变（挂起、补件、申诉都写它），
   * 拿它划日界会把「今天被谁碰过」当成「今天结的案」。{@code closed_at} 是进入终态的时间戳（申诉 48h 时限的起算点），语义唯一。
   *
   * <p><b>为什么要扫结案案件</b>：不变量 6 有一半是「结案了但没有结算单」——这种赔案在结算表里<b>一行都没有</b>，
   * 从结算侧出发永远扫不到。只按结算单逐笔对平，漏的恰恰是「钱根本没出去」的那一类。
   */
  @TenantScope(TenantScopeMode.AUTO)
  List<ClaimSettlementRef> findFinishedBetween(
      @Param("from") java.sql.Timestamp from, @Param("to") java.sql.Timestamp to);

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

  /**
   * 待重解析案件：指定原始码、案件级标准码尚未回填的<b>在办</b>案件（映射解决后重扫的输入）。
   *
   * <p>两个域各一个方法而不是按 {@code domain} 分支拼 Criteria：字段引用（{@code ::getHospitalCode} vs {@code
   * ::getDiagnosisCode}）在编译期绑定，无法用变量切换——用 if 在两个方法间分派更诚实。
   *
   * <p>只扫在办（状态集取反终态）：已结案案件的解析结果是历史事实，重扫不改写—— 映射纠错对存量终态案件的追溯属显式重放，不走隐式回填（同 {@link
   * ClaimItemRepository} 的幂等口径）。
   */
  default List<ClaimCase> findPendingByHospitalCode(Long tenantId, String rawHospitalCode) {
    return findByCriteria(
        Criteria.<ClaimCase>create()
            .eq(ClaimCase::getTenantId, tenantId)
            .eq(ClaimCase::getHospitalCode, rawHospitalCode)
            .eq(ClaimCase::getStdHospitalCode, "")
            .notIn(
                true,
                ClaimCase::getStatus,
                ClaimStatus.FINISHED.name(),
                ClaimStatus.CLOSED_REJECTED.name()));
  }

  /** 同 {@link #findPendingByHospitalCode}，诊断域（DISEASE）。 */
  default List<ClaimCase> findPendingByDiagnosisCode(Long tenantId, String rawDiagnosisCode) {
    return findByCriteria(
        Criteria.<ClaimCase>create()
            .eq(ClaimCase::getTenantId, tenantId)
            .eq(ClaimCase::getDiagnosisCode, rawDiagnosisCode)
            .eq(ClaimCase::getStdDiagnosisCode, "")
            .notIn(
                true,
                ClaimCase::getStatus,
                ClaimStatus.FINISHED.name(),
                ClaimStatus.CLOSED_REJECTED.name()));
  }
}
