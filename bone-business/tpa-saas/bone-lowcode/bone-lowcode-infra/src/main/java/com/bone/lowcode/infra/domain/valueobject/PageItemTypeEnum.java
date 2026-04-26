package com.bone.lowcode.infra.domain.valueobject;


import lombok.Getter;
//全配置页面元素类型枚举
@Getter
public enum PageItemTypeEnum {
    PAGE("Page"),

    FORM("Form"),

    CONTAINER("Container"),

    FIELD_SET("FieldSet"),

    TABLE("Table"),

    BUTTON("Button");

    private final String name;

    PageItemTypeEnum(String name) {
        this.name = name;
    }
}
