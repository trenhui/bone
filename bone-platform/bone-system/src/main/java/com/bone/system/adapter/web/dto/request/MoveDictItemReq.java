package com.bone.system.adapter.web.dto.request;

import lombok.Data;

/** 移动字典项请求（换父级 / 调排序），作用于指定层级视图。 */
@Data
public class MoveDictItemReq {
  /** 层级视图，缺省 DEFAULT。 */
  private String hierarchyCode;

  /** 置空表示提到根。 */
  private String parentCode;

  private Integer sort;
}
