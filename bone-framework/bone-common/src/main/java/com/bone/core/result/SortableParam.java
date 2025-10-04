package com.bone.core.result;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "可排序的分页参数")
@Data
public class SortableParam implements Query {

    @Schema(description = "排序字段")
    private List<SortingField> sortingFields;

}