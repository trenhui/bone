package com.bone.claim.domain.model.claim;

import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 赔案列表查询条件（领域读模型输入）。
 *
 * <p>与适配器 DTO 分离：HTTP 层允许字符串日期/任务维状态，领域层只接受已解析的 Instant 与案件状态集， 避免同一条件在 controller / service /
 * repository 三处各自解释。
 *
 * <p>所有可选条件为 null 时表示不过滤；{@code keywordClaimId} 是「纯数字关键字精确命中案件主键」的 补充条件，与其余模糊条件以 OR 组合，避免用户复制案件 ID
 * 却因只搜字符串列而查不到。
 */
public record ClaimCaseQuery(
    Long claimId,
    String keyword,
    Long keywordClaimId,
    String claimNo,
    Long reportId,
    String policyNo,
    List<ClaimStatus> statuses,
    String stage,
    String sourceChannel,
    String sourceSystem,
    String assigneeId,
    Integer hangUpStatus,
    Instant createdFrom,
    Instant createdTo,
    BigDecimal amountMin,
    BigDecimal amountMax) {

  /** 无条件查询（全部状态、按创建时间倒序）。 */
  public static ClaimCaseQuery all() {
    return new ClaimCaseQuery(
        null, null, null, null, null, null, List.of(), null, null, null, null, null, null, null,
        null, null);
  }
}
