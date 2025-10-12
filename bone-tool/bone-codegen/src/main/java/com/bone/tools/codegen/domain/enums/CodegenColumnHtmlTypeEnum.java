package com.bone.tools.codegen.domain.enums;

import lombok.Getter;

/**
 * 代码生成器的字段 HTML 展示枚举
 */
@Getter
public enum CodegenColumnHtmlTypeEnum {

    INPUT("input"), // 文本框
    TEXTAREA("textarea"), // 文本域
    SELECT("select"), // 下拉框
    RADIO("radio"), // 单选框
    CHECKBOX("checkbox"), // 复选框
    DATETIME("datetime"), // 日期控件
    IMAGE_UPLOAD("imageUpload"), // 上传图片
    FILE_UPLOAD("fileUpload"), // 上传文件
    EDITOR("editor"), // 富文本控件
    ;

    /**
     * 类型
     */
    private final String type;
    
    // 私有构造函数
    private CodegenColumnHtmlTypeEnum(String type) {
        this.type = type;
    }

}
