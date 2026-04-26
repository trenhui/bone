package com.bone.tpa.sdk.adjustment.enums;

public enum PersonalQuotaOperationEnum {

    INITIAL((byte) 1, "初始化个人额度"),
    QUOTA_CHANGE((byte) 2, "加减额度"),
    REMOVE_PEOPLE((byte) 3, "减人"),
    ;

    private final Byte code;
    private final String description;

    PersonalQuotaOperationEnum(Byte code, String description) {
        this.code = code;
        this.description = description;
    }

    public static PersonalQuotaOperationEnum getByCode(Byte code) {
        for (PersonalQuotaOperationEnum item : PersonalQuotaOperationEnum.values()) {
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
