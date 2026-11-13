package com.bone.masterdata.application.query.dto;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

/** 变更请求治理态（对客脱敏）：只回状态、段路由与裁决账号，不回任何业务字段值 / PII。 */
@Data
@Builder
public class ChangeRequestStateDTO {

  private String changeRequestId;
  private String partyId;
  private long targetVersion;
  private String status;
  private Map<String, SegmentState> segments;
  private int distinctApprovers;
  private String appliedBy;
  private int trailSize;
  private boolean activated;

  @Data
  @Builder
  public static class SegmentState {
    private String assignedOwner;
    private String state;
    private String decidedBy;
  }
}
