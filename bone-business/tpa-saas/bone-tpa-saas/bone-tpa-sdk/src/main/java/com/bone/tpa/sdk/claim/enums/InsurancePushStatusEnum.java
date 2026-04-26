package com.bone.tpa.sdk.claim.enums;

import lombok.Getter;

@Getter
public enum InsurancePushStatusEnum {

    WAITING_PUSH("保司未推送", 0),
    PUSH_SUCCESS("保司已推送", 1),
    PUSH_FAILED("保司推送失败", 2),
    COMPLETED("保司已完成", 3),

    ;

    private final String name;

    private final Integer value;

    InsurancePushStatusEnum(String name, Integer value) {
        this.name = name;
        this.value = value;
    }

}
