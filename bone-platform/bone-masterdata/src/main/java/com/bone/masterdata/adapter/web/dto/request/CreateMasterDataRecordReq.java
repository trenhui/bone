package com.bone.masterdata.adapter.web.dto.request;

import java.util.Map;
import lombok.Data;

@Data
public class CreateMasterDataRecordReq {
  private Long masterDataEntityId;
  private Map<String, Object> data;
}
