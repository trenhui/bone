package com.bone.lowcode.infra.domain.valueobject;

import lombok.AllArgsConstructor;
import lombok.Getter;

//基础组件枚举
@Getter
@AllArgsConstructor
public enum ComponentTypeEnum {
    INPUT("Input", "文本单行输入框"),
    SELECT_DROP("SelectDrop", "下拉框"),
    DATE_TIME("DateTime", "日期时间"),
    DATE_RANGE("DateRange", "日期区间"),
    SELECT_CTRL("SelectCtrl", "级联下拉框"),
    INPUT_NUM("InputNum", "数字单行输入框"),
    ;

    private final String type;
    private final String desc;

    public static ComponentTypeEnum getByType(String type) {
        for (ComponentTypeEnum sortTypeEnum : values()) {
            if (sortTypeEnum.getType().equals(type)) {
                return sortTypeEnum;
            }
        }
        return null;
    }
}
