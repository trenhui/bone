package com.bone.masterdata.adapter.web.dto.resp;

import java.time.LocalDateTime;
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
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
