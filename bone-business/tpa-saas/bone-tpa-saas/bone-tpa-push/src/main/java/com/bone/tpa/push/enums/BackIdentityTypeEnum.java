package com.bone.tpa.push.enums;

/**
 * @author luohuan
 * @date 2022/5/20
 */
public enum BackIdentityTypeEnum {
    TYPE_A("a","0"),
    TYPE_P("p","4"),
    TYPE_C("c","1"),
    TYPE_D("d","3"),
    TYPE_F("f","8"),
    TYPE_J("j","12"),
    TYPE_L("l","13"),
    TYPE_G("虚拟身份证","g"),
    TYPE_I("i", "6"),
    TYPE_ZHLH_A("a","01"),
    TYPE_ZHLH_R("r","02"),
    TYPE_ZHLH_C("c","03"),
    TYPE_ZHLH_O("o","13"),
    LWGMSFZ("ai","31");





    private String name;
    private String value;

    private BackIdentityTypeEnum(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public static String getMateName(String index) {
        for (BackIdentityTypeEnum b : BackIdentityTypeEnum.values()) {
            if (b.getValue().equals(index)) {
                return b.getName();
            }
        }
        return "i";
    }
}
