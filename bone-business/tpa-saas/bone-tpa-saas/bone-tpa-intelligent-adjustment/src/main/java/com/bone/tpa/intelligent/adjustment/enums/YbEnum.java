package com.bone.tpa.intelligent.adjustment.enums;

import lombok.Getter;

@Getter
public enum YbEnum {


    TRUE("true", 1),

    FALSE("false", 0),

    ;

    private final String code;
    private final Integer value;

    YbEnum(String code, Integer value) {
        this.code = code;
        this.value = value;
    }

    public static YbEnum getByCode(String code) {
        for (YbEnum e : YbEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }

        return null;
    }

}
