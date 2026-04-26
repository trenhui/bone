package com.bone.tpa.push.enums;

/**
 * @description:
 * @author: caixuepu
 * @time: 2021/5/10
 */
public enum AuditTypeEnum {
    mz("线上",0),
    zy("线下",1),
    ;
    private String name;
    private Integer value;

    private AuditTypeEnum(String name, Integer value) {
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
        for (AuditTypeEnum c : AuditTypeEnum.values()) {
            if (c.getValue() == index) {
                return c.getName();
            }
        }
        return "";
    }

    public static Integer getIndex(String name) {
        for (AuditTypeEnum c : AuditTypeEnum.values()) {
            if (c.getName().equals(name)) {
                return c.getValue();
            }
        }
        return null;
    }
}
