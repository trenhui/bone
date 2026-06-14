package com.bone.core.model;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 排序字段 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SortingField implements Serializable {

  /** 顺序 - 升序 */
  public static final String ORDER_ASC = "asc";

  /** 顺序 - 降序 */
  public static final String ORDER_DESC = "desc";

  /** 字段 */
  private String field;

  /** 顺序 */
  private String order = ORDER_ASC;
}
