package com.bone.tpa.claim.application.enums;

import lombok.Getter;

@Getter
public enum CheckTypeEnum {
    ALL((byte) 1, "所有数据校验后导入"),

    LINE_BY_LINE((byte) 1, "逐行导入"),

    ;

    private final byte code;
    private final String value;

    CheckTypeEnum(byte code, String value) {
        this.code = code;
        this.value = value;
    }

    public static CheckTypeEnum getByCode(byte code) {
        for (CheckTypeEnum e : CheckTypeEnum.values()) {
            if (code == e.code) {
                return e;
            }
        }
        return null;
    }

}
