package com.bone.system.application.query.qry;

import lombok.Data;

/** 字典项分页条件：类型必填（值域维度查询），父级 / 关键字 / 状态可选。 */
@Data
public class DictItemPageQuery {
  private String typeCode;
  private String parentCode;

  /** 按父级收窄时指定层级视图，缺省 DEFAULT。 */
  private String hierarchyCode;

  private String keyword;
  private Integer status;
  private int pageNum = 1;
  private int pageSize = 10;
}
