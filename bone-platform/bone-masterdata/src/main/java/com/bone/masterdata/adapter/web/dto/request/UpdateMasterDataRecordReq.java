package com.bone.masterdata.adapter.web.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
import lombok.Data;

@Data
public class UpdateMasterDataRecordReq {
  /** 同 create：md_record.data 为 NOT NULL，缺省需在入参校验阶段拦截为 400。 */
  @NotEmpty(message = "记录数据不能为空")
  private Map<String, Object> data;
}
