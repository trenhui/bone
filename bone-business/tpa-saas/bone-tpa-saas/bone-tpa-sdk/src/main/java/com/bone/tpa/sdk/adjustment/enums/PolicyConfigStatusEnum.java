package com.bone.tpa.sdk.adjustment.enums;

import lombok.Getter;

public enum PolicyConfigStatusEnum {


    INACTIVE("INACTIVE", "未启用", "ACTIVE"),
    ACTIVE("ACTIVE", "启用中", "PAUSE"),
    PAUSE("PAUSE", "停用中", "ACTIVE"),

    ;

    private String code;

    private String desc;

    private String nextStatus;


    PolicyConfigStatusEnum(String code, String desc, String nextStatus) {
        this.code = code;
        this.desc = desc;
        this.nextStatus = nextStatus;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public String getNextStatus() {
        return nextStatus;
    }

    static public PolicyConfigStatusEnum getByCode(String code){
        for (PolicyConfigStatusEnum status : PolicyConfigStatusEnum.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }
}
