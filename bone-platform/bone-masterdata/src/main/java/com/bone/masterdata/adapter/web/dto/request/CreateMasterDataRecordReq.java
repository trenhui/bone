package com.bone.masterdata.adapter.web.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

@Data
public class CreateMasterDataRecordReq {
  private Long masterDataEntityId;

  /** md_record.data 为 NOT NULL，缺省必须在此拦截为 400，否则落到 DB 约束会变成 500。 */
  @NotEmpty(message = "记录数据不能为空")
  private Map<String, Object> data;

  /** 业务唯一编码（租户+实体内唯一），下游按此定位记录。 */
  private String recordCode;

  /** 显示名称。 */
  private String displayName;

  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime effectiveFrom;

  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime effectiveTo;
}
