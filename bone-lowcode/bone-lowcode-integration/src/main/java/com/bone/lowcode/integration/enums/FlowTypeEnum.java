package com.bone.lowcode.integration.enums;

import lombok.Getter;

@Getter
public enum FlowTypeEnum {
    /**
     * 单流程
     */
    SINGLE_VERSION("single"),
    /**
     * 多流程
     */
    MULTI_VERSION("multi");

    private final String code;

    FlowTypeEnum(String code) {
        this.code = code;
    }

    public static FlowTypeEnum getByCode(String code) {
        for (FlowTypeEnum flowTypeEnum : FlowTypeEnum.values()) {
            if (flowTypeEnum.getCode().equals(code)) {
                return flowTypeEnum;
            }
        }

        throw new IllegalArgumentException("No FlowTypeEnum found with code " + code);
    }
}
