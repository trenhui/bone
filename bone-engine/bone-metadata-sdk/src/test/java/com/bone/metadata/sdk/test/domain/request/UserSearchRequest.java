package com.bone.metadata.sdk.test.domain.request;

import com.bone.core.model.SortablePageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserSearchRequest extends SortablePageParam {
  private String name;
  private Long roleId;
  private Integer offset;

  /** 由父类 {@code page}/{@code size}（1-based 页码）推导 OFFSET。 */
  public Integer getOffset() {
    return offset = (getPage() - 1) * getSize();
  }
}
