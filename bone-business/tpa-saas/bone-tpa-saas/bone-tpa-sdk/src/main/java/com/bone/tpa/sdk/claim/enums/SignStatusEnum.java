package com.bone.tpa.sdk.claim.enums;

import lombok.Getter;

/**
 * 签收状态枚举
 */
public enum SignStatusEnum {

    WAIT_FOR_PARTICIPANT("WAIT_FOR_PARTICIPANT", "待上传人员"),

    WAIT_FOR_IMAGE("WAIT_FOR_IMAGE", "待上传影像"),

    WAIT_FOR_CONFIRMATION("WAIT_FOR_CONFIRMATION", "待签收确认"),

    FINISHED("FINISHED", "已签收确认"),

    DELETED("DELETED", "已删除"),

    ;

    private final String code;
    private final String value;

    SignStatusEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static SignStatusEnum getByCode(String code) {
        for (SignStatusEnum e : SignStatusEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }

    public String getCode() {
        return code;
    }

    public String getValue() {
        return value;
    }
}
