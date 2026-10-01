package com.bone.masterdata.adapter.web.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;

@Data
public class UpdateMasterDataRecordReq {
  /** 同 create：md_record.data 为 NOT NULL，缺省需在入参校验阶段拦截为 400。 */
  @NotEmpty(message = "记录数据不能为空")
  private Map<String, Object> data;

  /** 业务唯一编码（草稿可改，发布态允许补登）。 */
  private String recordCode;

  /** 显示名称。 */
  private String displayName;

  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime effectiveFrom;

  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private LocalDateTime effectiveTo;
}
