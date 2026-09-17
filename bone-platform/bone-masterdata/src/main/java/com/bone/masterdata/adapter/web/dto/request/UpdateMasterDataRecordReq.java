package com.bone.masterdata.adapter.web.dto.request;

import java.util.Map;
import lombok.Data;

@Data
public class UpdateMasterDataRecordReq {
  private Map<String, Object> data;
}
