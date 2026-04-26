package com.bone.tpa.sdk.claim.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * 赔案复制后操作人枚举
 */
@Getter
public enum CopyOperatorTypeEnum {

    ORIGIN("ORIGIN", "原操作人"),
    CURRENT("CURRENT", "当前操作人"),
    SPECIFIC("SPECIFIC", "特定操作人"),

    ;

    private final String code;
    private final String description;

    CopyOperatorTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static Optional<CopyOperatorTypeEnum> getByCode(String code) {
        return Arrays.stream(values())
                .filter(e -> e.code.equals(code))
                .findFirst();
    }
}
