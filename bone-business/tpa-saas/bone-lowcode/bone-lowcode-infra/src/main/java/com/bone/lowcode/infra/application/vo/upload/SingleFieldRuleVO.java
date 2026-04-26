package com.bone.lowcode.infra.application.vo.upload;

import lombok.Data;

@Data
public class SingleFieldRuleVO {

    private String fieldId;

    private String bizName;

    private Boolean required;

    private Boolean unique;
}
