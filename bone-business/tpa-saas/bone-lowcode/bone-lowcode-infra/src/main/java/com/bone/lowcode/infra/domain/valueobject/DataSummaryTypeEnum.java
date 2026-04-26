package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

//汇总方式枚举
@Getter
public enum DataSummaryTypeEnum {

    SUM("sum", "汇总方式-求和"),
    AVERAGE("average", "汇总方式-平均"),
    COUNT("count", "汇总方式-计数"),
    ;

    private final String name;

    private final String desc;

    DataSummaryTypeEnum(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }
}
