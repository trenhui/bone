package com.bone.tpa.sdk.claim.enums;

/**
 * @description:
 * @author: caixuepu
 * @time: 2021/9/22
 */
public enum GyPushStatusEnum {

    UnPush("工银未推送",1),
    Pushed("工银已推送",2),
    Failed("工银推送失败",3),
    Finished("工银已完成",4);

    private String name;
    private Integer value;

    private GyPushStatusEnum(String name, Integer value) {
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
        for (GyPushStatusEnum c : GyPushStatusEnum.values()) {
            if (c.getValue() == index) {
                return c.getName();
            }
        }
        return "";
    }

    public static String getname(String name) {
        for (GyPushStatusEnum c : GyPushStatusEnum.values()) {
            if (c.getName().equals(name)) {
                return c.getName();
            }
        }
        return "";
    }
}
