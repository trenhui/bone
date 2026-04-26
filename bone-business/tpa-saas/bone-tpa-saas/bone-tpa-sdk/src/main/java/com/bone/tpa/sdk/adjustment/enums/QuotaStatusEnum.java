package com.bone.tpa.sdk.adjustment.enums;

import lombok.Getter;

/**
 * 控额状态
 * 同时也用作理算状态
 */
public enum QuotaStatusEnum {

    FAILED("FAILED", "失败理算记录"),

    FROZEN("FROZEN", "已冻结"),

    UNFROZEN("UNFROZEN", "已解冻"),

    CONFIRMED("CONFIRMED", "已确认扣减"),

    ROLLBACKED("ROLLBACKED", "已回滚"),


            ;

    private final String code;
    private final String value;

    QuotaStatusEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public String getCode() {
        return code;
    }

    public String getValue() {
        return value;
    }

    public static QuotaStatusEnum getByCode(String code) {
        for (QuotaStatusEnum e : QuotaStatusEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
