package com.bone.masterdata.adapter.web.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
import lombok.Data;

@Data
public class CreateMasterDataRecordReq {
  private Long masterDataEntityId;

  /** md_record.data 为 NOT NULL，缺省必须在此拦截为 400，否则落到 DB 约束会变成 500。 */
  @NotEmpty(message = "记录数据不能为空")
  private Map<String, Object> data;
}
