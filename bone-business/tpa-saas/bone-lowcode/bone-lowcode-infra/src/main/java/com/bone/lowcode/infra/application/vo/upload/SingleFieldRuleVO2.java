package com.bone.lowcode.infra.application.vo.upload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class SingleFieldRuleVO2 {

    private String bizName;

    private Boolean required;

    private Boolean unique;
}
