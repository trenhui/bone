package com.bone.lowcode.infra.application.vo.upload;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GroupFieldRuleVO2 {

    private List<String> fieldNameList;

    private Boolean unique;

    private List<String> fieldNameListA;

    private List<String> fieldNameListB;
}
