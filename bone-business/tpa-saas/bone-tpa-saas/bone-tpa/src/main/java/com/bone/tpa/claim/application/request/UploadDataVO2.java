package com.bone.tpa.claim.application.request;

import lombok.Data;

import java.util.List;

@Data
public class UploadDataVO2 extends BaseImportVO {

    /**
     * 适用数据模型的code列表
     */
    private List<String> modelCodeList;

    /**
     * 导入字段名称列表
     */
    private List<String> fieldNameList;

    /**
     * 单个字段规则
     */
    private List<SingleFieldRuleVO2> singleFieldRuleList;

    /**
     * 组合字段规则
     */
    private List<GroupFieldRuleVO2> groupFieldRuleList;

    /**
     * 模板文件名
     */
    private String templateFileName;

    /**
     * 文件数据上限
     */
    private Integer fileMaxCount;

    /**
     * 文件表头校验方式，1:按照模板表头名称一一对应
     */
    private Byte headerCheckMode;

    /**
     * 导入校验方式,1:所有数据校验后导入,2:逐行导入
     */
    private Byte checkType;

    /**
     * 导入操作说明
     */
    private String importDescription;
}
