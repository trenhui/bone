package com.bone.masterdata.adapter.web.dto.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MasterDataFieldDetailResp {
  private Long id;
  private Long masterDataEntityId;
  private String name;
  private String code;
  private String type;
  private Integer length;
  private Boolean required;
  private String defaultValue;
  private String description;
  private Integer sortOrder;
  private Instant createdAt;
  private Instant updatedAt;
}
