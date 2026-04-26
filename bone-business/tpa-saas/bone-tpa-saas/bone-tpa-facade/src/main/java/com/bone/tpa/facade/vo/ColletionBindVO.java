package com.bone.tpa.facade.vo;

import lombok.Data;

@Data
public class ColletionBindVO {
    private String bizIdentityCode  ;
    /**
     * 所属modelCode
     */
    private String modeCode ;
    /**
     * 字段code
     */
    private String fieldCode ;

    /**
     * 字段类型
     * 系统字段 or 扩展字段的枚举code
     */
    private String fieldType ;


    /**
     * 选项集 or  主数据的枚举code
     */
    private String bindType ;

    /**
     * 绑定目标
     * 如果是选项集，则返回选项集的code
     * 如果是主数据，则返回主数据数据类型的枚举code
     */
    private String bindTarget;


    private OptionSetDTO defaultValue;

}
