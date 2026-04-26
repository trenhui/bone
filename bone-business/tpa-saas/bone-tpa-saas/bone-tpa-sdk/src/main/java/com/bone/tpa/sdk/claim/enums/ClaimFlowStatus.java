package com.bone.tpa.sdk.claim.enums;

import lombok.Getter;

public enum ClaimFlowStatus {
    Draft( 0,"草稿"),

    Active(  1,"激活"),

    Inactive(2,"失效")
    ,

    ;

    private final Integer code;
    private final String desc;

    ClaimFlowStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static ClaimFlowStatus getByCode(Integer code) {
        for (ClaimFlowStatus e : ClaimFlowStatus.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }


}
