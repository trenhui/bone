package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.inspection.InspectionRecord;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.domain.annotation.Param;
import com.bone.metadata.sdk.domain.annotation.TenantScope;
import com.bone.metadata.sdk.domain.annotation.TenantScopeMode;
import java.time.Instant;
import java.util.List;

/**
 * 后督抽检仓储。自定义 SQL 按文件名约定绑定 {@code resources/sql/…/InspectionRepository/<方法名>.sql}（SDK 无
 * {@code @SqlQuery} 注解，与 {@code AppealRepository} 同款）。
 *
 * <p>租户口径 {@link TenantScopeMode#AUTO}：SQL 模板内以 {@code /*bone:tenant*}{@code /} 锚点注入租户条件——抽检单跨租户
 * 可见即构成越权读取（抽检单里含案件号与差异金额）。
 */
@TenantScope(TenantScopeMode.AUTO)
public interface InspectionRepository extends Repository<InspectionRecord, Long> {

  /** 按抽检单号查（对外引用键；租户内唯一）。 */
  InspectionRecord findByInspectionNo(@Param("inspectionNo") String inspectionNo);

  /** 按案件查最新抽检单（一期一案一检，用于重复发起幂等）。 */
  InspectionRecord findLatestByClaimId(@Param("claimId") long claimId);

  /**
   * 按结论时间窗取已结论的抽检单（自动通过错误预算的数据源）。
   *
   * <p><b>为什么只取 {@code CONCLUDED}</b>：{@code PENDING}/{@code IN_PROGRESS} 还没有结论，把它们算进分母会让错误率
   * 随抽检进度漂移——质检员今天多领几张单，错误率就「变好」，这和质量无关。
   *
   * <p><b>为什么按 {@code inspected_at} 而不是 {@code created_at}</b>：预算要回答「这段时间判出来的差异有多少」，
   * 结论时间才是事实发生时间；按发起时间取会漏掉「上周发起、本周才判完」的单子，而那批恰恰是最该被看到的。
   *
   * <p><b>上界写死在 SQL（{@code LIMIT 500}）</b>：同 {@code findConvergedInWindow}，这是运维判据的数据源，
   * 不是全库扫描器。调用方据「结果是否打满上界」标记 {@code truncated}，<b>不得静默裁剪</b>——截断后仍当作全量结论， 等于用一个有偏子集决定是否继续自动放款。
   *
   * <p>租户条件由 SQL 模板的 tenant 占位符注入，不接受入参租户。
   */
  List<InspectionRecord> findConcludedInWindow(
      @Param("from") Instant from, @Param("to") Instant to);
}
