package com.bone.integration.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
//@TableName("t_flow_definition")
public class FlowDefinitionDO extends BaseModel {

//    @TableId
    private Long id;

    private String appCode;
    /**
     * 流程Key,全局唯一
     */
    private String flowKey;

    /**
     * 流程名称
     */
    private String flowName;

    /**
     * 流程类型  sync：同步  async：异步
     */
    private String flowType;

    private String flowContent;

    /**
     * 流程描述
     */
    private String remark;

    private Integer deleted;
}
