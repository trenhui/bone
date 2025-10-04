package com.bone.integration.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class FlowVersionDO extends BaseModel {
    private Long id;
    private String appCode;
    private Long flowId;
    private String flowKey;
    private String flowVersion;
    /**
     * 流程状态   0:禁用  1:启用
     */
    private Integer flowVersionStatus;
    /**
     * 流程版本描述
     */
    private String flowVersionRemark;
    private String flowContent;
    private String flowType;
    private String inputs;
    private String outputs;
    private String variables;
    private Integer deleted;
}
