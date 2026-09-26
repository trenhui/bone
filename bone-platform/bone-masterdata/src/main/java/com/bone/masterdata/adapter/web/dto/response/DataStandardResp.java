package com.bone.masterdata.adapter.web.dto.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataStandardResp {
  private Long id;
  private String entityCode;
  private String fieldCode;
  private Integer ruleType;
  private String pattern;
  private String refCode;
  private String description;
  private Instant createdAt;
  private Instant updatedAt;
}
