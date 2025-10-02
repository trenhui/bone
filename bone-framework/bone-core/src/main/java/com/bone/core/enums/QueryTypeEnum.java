package com.bone.core.enums;

import lombok.Getter;

@Getter
public enum QueryTypeEnum {
    EQUAL("EQUAL", "精准查询"),
    LIKE("LIKE", "前精准后模糊查询"),
    GREATER_THAN("GREATER_THAN", "大于查询"),
    LESS_THAN("LESS_THAN", "小于查询"),
    NOT_EQUAL("NOT_EQUAL", "不等于查询"),
    IN("IN", "范围查询"),
    ALL_LIKE("ALL_LIKE", "全模糊查询"),
    BETWEEN("BETWEEN", "区间查询");

    private final String code;
    private final String value;

    QueryTypeEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static QueryTypeEnum getByCode(String code) {
        for (QueryTypeEnum e : QueryTypeEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}