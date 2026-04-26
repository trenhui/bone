package com.bone.tpa.push.enums;

/**
 * @description:
 * @author: caixuepu
 * @time: 2021/5/10
 */
public enum  TreatmentTypeEnum {
    mz("门/急诊",1),
    zy("住院",2),
    yf("药房",3),
    other("其他",4),
    mz_tmb("门诊-慢特病",5);
    private String name;
    private Integer value;

    private TreatmentTypeEnum(String name, Integer value) {
        this.name = name;
        this.value = value;
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

    public static String getname(int index) {
        for (TreatmentTypeEnum c : TreatmentTypeEnum.values()) {
            if (c.getValue() == index) {
                return c.getName();
            }
        }
        return "";
    }

    public static Integer getIndex(String name) {
        for (TreatmentTypeEnum c : TreatmentTypeEnum.values()) {
            if (c.getName().equals(name)) {
                return c.getValue();
            }
        }
        return null;
    }
}
