package com.bone.lowcode.infra.application.dto.tableRule;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateSequenceDTO {

    /**
     * 1:表格行动态,2:表间动态
     */
    @NotNull(message = "规则类型不能为空,1:表格行规则,2:表间规则")
    private Integer type;

    /**
     * 规则ID
     */
    @NotNull(message = "规则ID不能为空")
    private Long ruleId;

    /**
     * 序号
     */
    @NotNull(message = "规则序号不能为空")
    private Integer sequence;
}
