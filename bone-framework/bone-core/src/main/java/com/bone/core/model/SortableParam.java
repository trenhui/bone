package com.bone.core.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Schema(description = "可排序的分页参数")
@Data
public class SortableParam implements Query {

  @Schema(description = "排序字段")
  private List<SortingField> sortingFields;
}
