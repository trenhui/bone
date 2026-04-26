package com.bone.tpa.sdk.claim.enums;

public enum ImportTypeEnum {
    ADD("ADD", "新增", 1),

    REPLACE("REPLACE", "替换", 2),

    ;

    private final String code;
    private final String value;
    private final Integer configCode;

    ImportTypeEnum(String code, String value, Integer configCode) {
        this.code = code;
        this.value = value;
        this.configCode = configCode;
    }

    public static ImportTypeEnum getByCode(String code) {
        for (ImportTypeEnum e : ImportTypeEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }

    public static ImportTypeEnum getByConfigCode(Integer configCode) {
        for (ImportTypeEnum e : ImportTypeEnum.values()) {
            if (e.getConfigCode().equals(configCode)) {
                return e;
            }
        }
        return null;
    }

    public String getCode() {
        return code;
    }

    public String getValue() {
        return value;
    }

    public Integer getConfigCode() {
        return configCode;
    }
}
