package com.bone.lowcode.infra.application.vo.page.pageJson;

import com.bone.lowcode.infra.application.vo.fieldTableRule.FieldTableRuleVO;
import lombok.Data;

import java.util.List;

@Data
public class PageRules {

    /**
     * 提交规则
     */
    List<SubmitRule> submitRuleList;

    /**
     * 字段联动规则
     */
    List<FieldLinkageRule> fieldLinkageRuleList;

    /**
     * 字段影响表格属性的规则
     */
    List<FieldTableRuleVO> fieldTableRuleVOList;

    /**
     * 字段联动展示规则
     */
    List<LinkedDisplayRuleVO> linkedDisplayRuleVOList;
}
