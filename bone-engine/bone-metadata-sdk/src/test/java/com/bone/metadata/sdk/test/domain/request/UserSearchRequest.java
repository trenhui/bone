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
}
