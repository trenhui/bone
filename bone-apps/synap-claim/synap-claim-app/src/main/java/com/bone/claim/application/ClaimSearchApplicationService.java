package com.bone.claim.application;

import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.claim.projection.ClaimSearchHit;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 工作台跨键检索。安全下限 = @PreAuthorize（功能权限）+ SDK @TenantScope（租户隔离）
// + 模板内 /*bone:tenant*/ 锚点 + 结果字段最小化；不声称已实现按人/按组的行级 ACL。
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimSearchApplicationService {

  private final ClaimCaseRepository claimCaseRepository;

  @Transactional(readOnly = true)
  public List<ClaimSearchHit> search(String rawKeyword, int limit) {
    String problem = ClaimSearchRules.validate(rawKeyword, limit);
    if (problem != null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_SEARCH_KEYWORD_INVALID, problem);
    }
    List<ClaimSearchHit> hits =
        claimCaseRepository.searchByKeyword(ClaimSearchRules.toLikePattern(rawKeyword), limit);
    // 审计日志只记长度与命中数，绝不记原始关键词（可能是姓名/手机号）。
    log.info(
        "[CLAIM_SEARCH] keywordLength={} limit={} hits={} explicitReject=false",
        rawKeyword.trim().length(),
        limit,
        hits.size());
    return hits;
  }
}
