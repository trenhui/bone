package com.bone.masterdata.application.query.dto;

import java.util.Date;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QualityCheckDTO {
  private Long id;
  private Long masterDataEntityId;
  private Date startedAt;
  private Date endedAt;
  private String status;
  private Integer totalRecords;
  private Integer passedRecords;
  private Integer failedRecords;
}
