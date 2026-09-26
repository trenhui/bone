package com.bone.system.adapter.web.dto.request;

import lombok.Data;

/** 字典类型分页查询条件。 */
@Data
public class DictTypePageReq {
  private String keyword;
  private String category;
  private String moduleCode;
  private Integer status;
  private int pageNum = 1;
  private int pageSize = 10;
}
