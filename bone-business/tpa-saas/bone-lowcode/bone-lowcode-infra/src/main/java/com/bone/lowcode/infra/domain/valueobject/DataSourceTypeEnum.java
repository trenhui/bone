package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

//下拉框数据来源类型枚举
@Getter
public enum DataSourceTypeEnum {
    OPTION_SET((byte) 1, "选项集"),
    MASTER_DATA((byte) 2, "主数据"),
    ENUM_DATA((byte) 3, "枚举数据");

    private final Byte type;
    private final String desc;

    DataSourceTypeEnum(Byte value, String desc) {
        this.type = value;
        this.desc = desc;
    }
}
