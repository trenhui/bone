package com.bone.system.adapter.web.dto.request;

import lombok.Data;

/** 字典项分页查询条件（值域维度）。 */
@Data
public class DictItemPageReq {
  private String typeCode;
  private String parentCode;
  private String hierarchyCode;
  private String keyword;
  private Integer status;
  private int pageNum = 1;
  private int pageSize = 10;
}
