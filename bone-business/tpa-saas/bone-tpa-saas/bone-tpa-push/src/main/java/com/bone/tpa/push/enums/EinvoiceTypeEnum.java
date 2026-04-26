package com.bone.tpa.push.enums;

/**
 * @description:
 * @author: caixuepu
 * @time: 2021/9/6
 */
public enum EinvoiceTypeEnum {

    ZZ("纸质",(Integer)0, "00"),
    DZ("电子",(Integer)1, "01"),
    ;

    private String name;
    private Integer value;
    private String thirdValue;

    private EinvoiceTypeEnum(String name, Integer value, String saasValue) {
        this.name = name;
        this.value = value;
        this.thirdValue = saasValue;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public String getThirdValue() {
        return thirdValue;
    }

    public void setThirdValue(String thirdValue) {
        this.thirdValue = thirdValue;
    }

    public static String getname(Integer index) {
        for (EinvoiceTypeEnum c : EinvoiceTypeEnum.values()) {
            if (c.getValue() == index) {
                return c.getName();
            }
        }
        return "";
    }

    public static Integer getValue(String name) {
        for (EinvoiceTypeEnum c : EinvoiceTypeEnum.values()) {
            if (c.getName().equals(name)) {
                return c.getValue();
            }
        }
        return null;
    }

    public static Integer getTpaValue(String saasValue) {
        for (EinvoiceTypeEnum c : EinvoiceTypeEnum.values()) {
            if (c.getThirdValue().equals(saasValue)) {
                return c.getValue();
            }
        }
        return null;
    }
}
