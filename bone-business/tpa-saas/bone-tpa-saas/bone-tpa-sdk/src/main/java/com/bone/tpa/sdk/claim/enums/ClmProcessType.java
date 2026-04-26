package com.bone.tpa.sdk.claim.enums;

import lombok.Getter;

/**
 * 02半流程,01全流程,04录审分离
 */
public enum ClmProcessType {
    FULL_PROCESS("01", "全流程"),
    HALF_PROCESS("02", "半流程"),
    RECORD_AUDIT("04", "录审分离"),
    ;
    private String code ;

    private String desc;

    ClmProcessType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    public static ClmProcessType getEnum(String code) {
        for (ClmProcessType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
