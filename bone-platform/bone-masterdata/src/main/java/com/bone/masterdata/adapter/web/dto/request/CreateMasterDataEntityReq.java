package com.bone.masterdata.adapter.web.dto.request;

import lombok.Data;

@Data
public class CreateMasterDataEntityReq {
  private String name;
  private String code;
  private String description;
  private String category;
}
