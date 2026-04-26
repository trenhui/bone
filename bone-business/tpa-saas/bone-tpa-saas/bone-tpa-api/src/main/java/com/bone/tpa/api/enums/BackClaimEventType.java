package com.bone.tpa.api.enums;

import lombok.Getter;

@Getter
public enum BackClaimEventType {
    /**
     * tpa发起退回事件，saas变成质检中
     */
    tpa退回质检("100", "saas", "tpa退回质检"),
    ;


    private String code ;

    /**
     * 接收事件系统名
     */
    private String system;
    private String desc;

    BackClaimEventType(String code, String system, String desc) {
        this.code = code;
        this.system= system;
        this.desc = desc;
    }

    public static BackClaimEventType getEnum(String code) {
        for (BackClaimEventType type : BackClaimEventType.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }


}
