package com.bone.lowcode.infra.application.vo.page.pageJson;

import com.bone.lowcode.infra.application.vo.optionSet.FieldLinkedDisplayRuleVO;
import lombok.Data;

import java.util.List;

@Data
public class LinkedDisplayRuleVO {
    /**
     * 下拉字段id
     */
    private String selectFieldId;

    /**
     * 字段联动展示规则中受影响的字段-扩展属性
     */
    List<FieldLinkedDisplayRuleVO> otherField;
}
