package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

//模型归属者枚举
@Getter
public enum ModelOwnerEnum {
    FIELD_SET((byte) 1, "区块字段"),
    TABLE((byte) 2, "表格"),
    PAGE_HEAD((byte) 3, "页头"),
    UPLOAD_DATA((byte) 4, "上传数据组件"),
    ;

    private final byte code;
    private final String desc;

    ModelOwnerEnum(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
