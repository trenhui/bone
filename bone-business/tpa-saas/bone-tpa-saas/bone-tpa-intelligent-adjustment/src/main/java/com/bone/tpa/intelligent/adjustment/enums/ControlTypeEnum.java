package com.bone.tpa.intelligent.adjustment.enums;

import lombok.Getter;

/**
 * 控制方式，是否强行，黑白名单等
 */
@Getter
public enum ControlTypeEnum {

    FORCE("FORCE", "强制"),

    ALARM("ALARM", "告知，仅弹窗提醒"),

    WHITE("WHITE", "白名单"),

    BLACK("BLACK", "黑名单"),

    ;

    private final String code;
    private final String value;

    ControlTypeEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static ControlTypeEnum getByCode(String code) {
        for (ControlTypeEnum e : ControlTypeEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
