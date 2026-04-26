package com.bone.tpa.sdk.adjustment.enums;

import lombok.Getter;

/**
 * 责任账户类型
 */
public enum AccountTypeEnum {

    /**
     * 无账户
     */
    NO_ACCOUNT("NO_ACCOUNT","无账户", 0),

    /**
     * 公账
     */
    PUBLIC("PUBLIC","公账", 1),

    /**
     * 个账
     */
    PERSONAL("PERSONAL","个账", 2);

    private final String code;
    private final String description;
    private final int pushValue;

    @Override
    public String toString() {
        return getCode();
    }

    AccountTypeEnum(String code, String description, int pushValue) {
        this.code = code;
        this.description = description;
        this.pushValue = pushValue;
    }

    public static AccountTypeEnum getByCode(String code) {
        for (AccountTypeEnum accountType : AccountTypeEnum.values()) {
            if (accountType.getCode().equals(code)) {
                return accountType;
            }
        }
        return null;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public int getPushValue() {
        return pushValue;
    }
}
