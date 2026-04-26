package com.bone.tpa.sdk.claim.enums;

import lombok.Getter;

@Getter
public enum ClaimActionEnum {
    DRAFT("DRAFT", "临时保存数据" ),
    COMPLETE("COMPLETE", "完成审批" ),

    ;
    private String code ;
    private String value;
    ClaimActionEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }
    public static ClaimActionEnum getByCode(String code) {
        for (ClaimActionEnum claimActionEnum : ClaimActionEnum.values()) {
            if (claimActionEnum.getCode().equals(code)) {
                return claimActionEnum;
            }
        }
        return null;
    }
}
