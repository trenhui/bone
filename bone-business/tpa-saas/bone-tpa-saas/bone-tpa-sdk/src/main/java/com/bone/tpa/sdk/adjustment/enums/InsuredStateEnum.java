package com.bone.tpa.sdk.adjustment.enums;

public enum InsuredStateEnum {

    NORMAL((byte) 0, "正常"),
    REMOVED((byte) 1, "已减人"),
    ;

    private final Byte code;
    private final String description;

    InsuredStateEnum(Byte code, String description) {
        this.code = code;
        this.description = description;
    }

    public static InsuredStateEnum getByCode(Byte code) {
        for (InsuredStateEnum item : InsuredStateEnum.values()) {
            if (item.getCode().equals(code)) {
                return item;
            }
        }
        return null;
    }

    public Byte getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
