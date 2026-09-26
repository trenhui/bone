package com.bone.system.application.query.qry;

import lombok.Data;

/** 字典类型分页条件：命中 code / name 关键字，按分类、模块、状态精确过滤。 */
@Data
public class DictTypePageQuery {
  private String keyword;
  private String category;
  private String moduleCode;
  private Integer status;
  private int pageNum = 1;
  private int pageSize = 10;
}
