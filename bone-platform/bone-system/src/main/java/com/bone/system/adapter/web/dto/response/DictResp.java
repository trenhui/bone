package com.bone.system.adapter.web.dto.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DictResp {
  private Long id;
  private String type;
  private String typeName;
  private String code;
  private String label;
  private String value;
  private Integer sort;
  private Integer status;
  private Instant createdAt;
  private Instant updatedAt;
}
