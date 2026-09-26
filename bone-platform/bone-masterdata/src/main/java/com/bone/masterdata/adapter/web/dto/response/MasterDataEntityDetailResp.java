package com.bone.masterdata.adapter.web.dto.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MasterDataEntityDetailResp {
  private Long id;
  private String name;
  private String code;
  private String description;
  private String category;
  private String status;
  private Integer version;
  private int fieldCount;
  private Instant createdAt;
  private Instant updatedAt;
}
