package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

//事件触发器归属枚举
@Getter
public enum TriggerOwnerEnum {
    FORM((byte) 0, "form"),
    BLOCK((byte) 1, "block"),
    TABLE_ROW((byte) 2, "table行"),
    TABLE_LEFT((byte) 3, "table左表头"),
    TABLE_RIGHT((byte) 4, "table右表头"),
    ;
    private final byte code;
    private final String desc;

    TriggerOwnerEnum(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static String getDescByCode(byte code) {
        for (TriggerOwnerEnum value : TriggerOwnerEnum.values()) {
            if (value.getCode() == code) {
                return value.getDesc();
            }
        }
        return null;
    }
}
