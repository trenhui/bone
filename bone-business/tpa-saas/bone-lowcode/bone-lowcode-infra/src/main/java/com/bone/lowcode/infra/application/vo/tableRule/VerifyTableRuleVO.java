package com.bone.lowcode.infra.application.vo.tableRule;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyTableRuleVO {

    private Integer type;//1:表格行,2:表间

    private Object rule;
}
