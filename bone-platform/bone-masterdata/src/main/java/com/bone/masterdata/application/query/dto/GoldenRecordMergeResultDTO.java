package com.bone.masterdata.application.query.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 合并/回滚用例的出站结果（对客脱敏）。
 *
 * <p><b>PII 硬约束</b>：只回传身份、版本、裁决与摘要，<b>绝不回传任何字段值</b>。字段值只存在于平台快照与 消费侧按 partyId 拉取的授权视图中；事件信封亦只携带
 * payloadHash。
 */
@Data
@Builder
public class GoldenRecordMergeResultDTO {

  private String outcome;
  private String mergeId;
  private String partyId;
  private long version;
  private String reason;
  private String idempotencyKey;
  private String payloadHash;
  private int fieldCount;
  private boolean dispatched;
}
