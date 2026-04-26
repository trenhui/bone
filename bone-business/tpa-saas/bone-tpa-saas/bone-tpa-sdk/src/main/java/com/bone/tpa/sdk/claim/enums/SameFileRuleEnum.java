package com.bone.tpa.sdk.claim.enums;

public enum SameFileRuleEnum {
    KEEP("KEEP", "新增且保留两者", (byte) 1),

    REPLACE("REPLACE", "新增且覆盖", (byte) 2),

    ;

    private final String code;
    private final String value;
    private final Byte configCode;

    SameFileRuleEnum(String code, String value, Byte configCode) {
        this.code = code;
        this.value = value;
        this.configCode = configCode;
    }

    public static SameFileRuleEnum getByCode(String code) {
        for (SameFileRuleEnum e : SameFileRuleEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }

    public static SameFileRuleEnum getByConfigCode(Byte configCode) {
        for (SameFileRuleEnum e : SameFileRuleEnum.values()) {
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

    public Byte getConfigCode() {
        return configCode;
    }
}
