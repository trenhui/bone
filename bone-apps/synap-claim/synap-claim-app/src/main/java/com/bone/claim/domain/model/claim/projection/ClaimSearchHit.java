package com.bone.claim.domain.model.claim.projection;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 跨键检索结果：只回最小字段，绝不回 name_masked / phone_masked / person_id_hash 的值。
// matchedKey 是受控分类码，不是被匹配到的原始 PII。
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClaimSearchHit {
  private Long claimId;
  private String claimNo;
  private String policyNo;
  private String status;
  private String stage;
  private String matchedKey;
  private Instant createdAt;
}
