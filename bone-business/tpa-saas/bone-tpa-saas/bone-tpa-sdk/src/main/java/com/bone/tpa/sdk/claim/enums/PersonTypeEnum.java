package com.bone.tpa.sdk.claim.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum PersonTypeEnum {

    OUT_INSURE("OUT_INSURE", "出险人"),
    MAIN_INSURE("MAIN_INSURE", "主被保人"),
    BENEFIT("BENEFIT", "受益人"),
    COLLECT("COLLECT", "领款人"),
    COLLECT_BUSINESS("COLLECT_BUSINESS", "领款单位"),
    APPLY("APPLY", "申请人"),

    ;

    private final String code;
    private final String description;

    PersonTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static Optional<PersonTypeEnum> getByCode(String code) {
        return Arrays.stream(values())
                .filter(e -> e.code.equals(code))
                .findFirst();
    }
}
