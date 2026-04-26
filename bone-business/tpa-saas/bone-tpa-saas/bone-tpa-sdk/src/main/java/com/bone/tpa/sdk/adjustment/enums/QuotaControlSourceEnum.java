package com.bone.tpa.sdk.adjustment.enums;

import lombok.Getter;

/**
 * 控额方枚举
 */
public enum QuotaControlSourceEnum {

    TPA("TPA", "普康TPA控额", 2),

    DIRECT("DIRECT", "普康直付控额", 1),

    INSURER("INSURER", "保司三方接口", 3), //仅预留

    ;

    private final String code;
    private final String value;
    private final int pushValue;

    QuotaControlSourceEnum(String code, String value, int pushValue) {
        this.code = code;
        this.value = value;
        this.pushValue = pushValue;
    }

    public String getCode() {
        return code;
    }

    public String getValue() {
        return value;
    }

    public int getPushValue() {
        return pushValue;
    }

    public static QuotaControlSourceEnum getByCode(String code) {
        for (QuotaControlSourceEnum e : QuotaControlSourceEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
