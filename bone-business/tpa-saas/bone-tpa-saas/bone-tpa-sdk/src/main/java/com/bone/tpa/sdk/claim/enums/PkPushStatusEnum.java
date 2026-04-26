package com.bone.tpa.sdk.claim.enums;

import lombok.Getter;

@Getter
public enum PkPushStatusEnum {

    WAITING_PUSH("未推送", 1),
    PUSH_SUCCESS("已推送", 2),
    ;


    private final String name;

    private final Integer value;

    PkPushStatusEnum(String name, Integer value) {
        this.name = name;
        this.value = value;
    }

}
