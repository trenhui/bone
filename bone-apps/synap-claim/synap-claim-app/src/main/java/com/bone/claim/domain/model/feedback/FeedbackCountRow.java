package com.bone.claim.domain.model.feedback;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 汇总 SQL 的行投影。SDK SmartRowMapper 走无参构造 + 反射写字段，
// 故不能用 record（实测 Failed to instantiate）。
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackCountRow {
  private String bucket;
  private Long eventCount;
}
