package com.bone.tpa.sdk.adjustment.enums;

public enum LiabilityToBindStatusEnum {

    /**
     * 表示该赔案可以被绑定
     */
    ACTIVE("ACTIVE", "可以被绑定"),
    /**
     * 表示该赔案不能被绑定
     * 置灰
     */
    INACTIVE("INACTIVE", "不可被绑定"),
    /**
     * 已经被删除了
     */
    DELETED("DELETED", "已删除"),

    ;

    private final String code;
    private final String value;

    LiabilityToBindStatusEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static LiabilityToBindStatusEnum getByCode(String code) {
        for (LiabilityToBindStatusEnum e : LiabilityToBindStatusEnum.values()) {
            if (e.getCode().equals(code)) {
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
}
