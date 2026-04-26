package com.bone.tpa.sdk.adjustment.enums;

import lombok.Getter;

@Getter
public enum PlanStatus {
    DRAFT("DRAFT", "草稿"),

    ACTIVE("ACTIVE", "启用"),

    INACTIVE("INACTIVE", "停用"),

    ;

    PlanStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private String code;

    private String desc;

    static public PlanStatus getByCode(String code){
        for (PlanStatus status : PlanStatus.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }
}
