package com.bone.tpa.push.enums;

import lombok.Getter;

import java.util.Objects;


/**
 * @author 29854
 */

@Getter
public enum ClaimChannelEnum {
    DH("鼎和", "dh"),
    PKB("普康宝", "pkb"),
    PKH("普康荟", "pkh"),
    TXF("太享福", "txf"),
    YCHGJ("永诚好管家", "ychgj"),
    YDCA("英大长安", "ydca"),
    CHINA_POST("中邮", "chinapost"),



    ;


    private String name;
    private String value;

    private ClaimChannelEnum(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public static String getName(String value) {
        for (ClaimChannelEnum c : ClaimChannelEnum.values()) {
            if (Objects.equals(c.getValue(), value)) {
                return c.getName();
            }
        }
        return "";
    }

    public static String getValue(String name) {
        for (ClaimChannelEnum c : ClaimChannelEnum.values()) {
            if (c.getName().equals(name)) {
                return c.getValue();
            }
        }
        return null;
    }
}
