package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

//展示状态枚举
@Getter
public enum PublishStatusEnum {
    EDIT("1", "编辑态"),
    PUBLISH("2", "发布态");
    private final String code;
    private final String desc;

    PublishStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
