package com.bone.tpa.sdk.adjustment.enums;

import lombok.Getter;

/**
 * 初审赔案类型枚举 0-线上，1-线下
 */
public enum ClaimAuditTypeEnum {
    DRAFT(0, "线上"),
    ACTIVE(1, "线下"),
    ;

    private final Integer code;
    private final String desc;

    ClaimAuditTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static String getDescByCode(Integer code) {
        for (ClaimAuditTypeEnum item : ClaimAuditTypeEnum.values()) {
            if (item.getCode().equals(code)) {
                return item.getDesc();
            }
        }
        return null;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
