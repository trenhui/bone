package com.bone.lowcode.infra.application.dto.optionSet;

import com.bone.lowcode.infra.domain.model.LinkedDisplayRuleEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class FieldLinkedDisplayRuleDTO {

    /**
     * 下拉字段id
     */
    @NotNull(message = "SelectDrop下拉字段id不能为空")
    private Long selectFieldId;

    /**
     * 下拉框选项数据源类型,1:选项集,2:主数据
     */
    @NotNull(message = "下拉框选项数据源类型不能为空")
    private Byte datasourceType;

    /**
     * 下拉框选项数据源code
     */
    @NotEmpty(message = "下拉框选项数据源code不能为空")
    private String datasourceCode;

    /**
     * 键值对,被影响字段id及扩展属性名
     */
    @Valid
    private List<LinkedDisplayRuleEntry> entryList;
}
