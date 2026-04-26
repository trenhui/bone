package com.bone.tpa.sdk.adjustment.enums;

import lombok.Getter;

public enum PersonalQuotaCalculateEnum {

    FREEZE("FREEZE", "冻结额度"),
    UNFREEZE("UNFREEZE", "解冻额度"),

    ;

    private final String code;
    private final String description;

    PersonalQuotaCalculateEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static PersonalQuotaCalculateEnum getByCode(String code) {
        for (PersonalQuotaCalculateEnum item : PersonalQuotaCalculateEnum.values()) {
            if (item.getCode().equals(code)) {
                return item;
            }
        }
        return null;
    }
}
