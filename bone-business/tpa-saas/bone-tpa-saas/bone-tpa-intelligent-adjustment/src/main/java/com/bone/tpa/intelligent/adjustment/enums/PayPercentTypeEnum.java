package com.bone.tpa.intelligent.adjustment.enums;

import lombok.Getter;

/**
 * 赔付比例类型
 */
@Getter
public enum PayPercentTypeEnum {

    DIFFERENT("DIFFERENT", "不同比例"),

    SAME("SAME", "同一比例"),

    ;

    private final String code;
    private final String value;

    PayPercentTypeEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static PayPercentTypeEnum getByCode(String code) {
        for (PayPercentTypeEnum e : PayPercentTypeEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
