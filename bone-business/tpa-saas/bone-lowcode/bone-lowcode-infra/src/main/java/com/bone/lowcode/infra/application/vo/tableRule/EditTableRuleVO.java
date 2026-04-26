package com.bone.lowcode.infra.application.vo.tableRule;

import com.bone.lowcode.infra.application.vo.Sequence;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EditTableRuleVO {

    private Integer type;//1:表格行,2:表间

    private Sequence rule;
}
