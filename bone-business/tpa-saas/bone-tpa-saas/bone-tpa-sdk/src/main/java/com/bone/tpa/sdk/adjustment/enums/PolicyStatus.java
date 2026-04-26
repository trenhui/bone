package com.bone.tpa.sdk.adjustment.enums;

import lombok.Getter;

public enum PolicyStatus {

    UNSET(-1, "未设置"),
    INIT(0, "待审核"),
    ACTIVE(1, "已生效"),
    DESERTED(2, "已作废"),
    FROZEN(3, "已冻结"),

    ;

    PolicyStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private Integer code;

    private String desc;

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    static public PolicyStatus getByCode(Integer code){
        for (PolicyStatus status : PolicyStatus.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }
}
