package com.bone.masterdata.adapter.web.dto.request;

import lombok.Data;

@Data
public class UpdateMasterDataEntityReq {
  private String name;
  private String description;
  private String category;
}
