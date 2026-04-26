package com.bone.lowcode.infra.application.dto.scriptRule;

import lombok.Data;

@Data
public class CreateScriptRuleDTO {

    /**
     * 规则名称
     */
    private String name;

    /**
     * 规则类型,1:字段动态,2:表格动态,3:表格报存,4:提交条件
     */
    private Byte type;

    /**
     * 触发时机,1:页面初始化后,2:表单输入框失焦,3:表单选择框值变化,4:表格单行改变,5:表格单行保存校验,6:表格单行保存完成后,7:提交校验
     */
    private Byte triggerTime;

    /**
     * 触发字段对象
     */
    private String field;

    /**
     * 触发表格对象
     */
    private String table;

    /**
     * 脚本内容
     */
    private String scriptContent;

    /**
     * 规则描述
     */
    private String description;
}
