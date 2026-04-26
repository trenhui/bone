package com.bone.lowcode.infra.domain.valueobject;


import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
//全配置页面的code枚举
@Getter
public enum BasicPageCodeEnum {

    ENTRY("entry", "录入页面"),
    QUALITY_CHECK("qualitycheck", "质检页面"),
    AUDIT("audit", "审核页面"),
    REVIEW("review", "复核页面"),
    NEW_SIGN("newSign", "新批次签收页面"),
    ;
    private final String code;
    private final String desc;

    BasicPageCodeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static List<String> getAllBasicPageCode() {
        List<String> re = new ArrayList<>();
        for (BasicPageCodeEnum item : BasicPageCodeEnum.values()) {
            re.add(item.getCode());
        }
        return re;
    }
}
