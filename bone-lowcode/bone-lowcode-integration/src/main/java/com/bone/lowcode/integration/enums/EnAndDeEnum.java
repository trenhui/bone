package com.bone.lowcode.integration.enums;

public enum EnAndDeEnum {
    ENCRYPT("加密"),
    DECRYPT("解密"),
    SIGN_REQUIRED("是"),
    NONE("无");

    private final String value;

    EnAndDeEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static EnAndDeEnum fromValue(String value) {
        // 如果传入值为空或无效，则返回 NONE，表示不需要加签
        if (value == null || value.isEmpty()) {
            return EnAndDeEnum.NONE;
        }

        // 遍历枚举值查找匹配项
        for (EnAndDeEnum enAndDeEnum : EnAndDeEnum.values()) {
            if (enAndDeEnum.getValue().equals(value)) {
                return enAndDeEnum;
            }
        }

        // 如果找不到匹配项，也返回 NONE
        return EnAndDeEnum.NONE;
    }
}
