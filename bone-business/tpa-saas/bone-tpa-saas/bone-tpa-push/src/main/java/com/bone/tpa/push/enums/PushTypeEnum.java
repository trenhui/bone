package com.bone.tpa.push.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author feihaiming
 * @create 2025/10/16 17:59
 */
@Getter
@AllArgsConstructor
public enum PushTypeEnum {
    DB_PUSH("db", "数据库推送"),
    HTTP_PUSH("http", "http推送"),
    DUBBO_PUSH("dubbo", "dubbo推送"),
    ;


    private String code;
    private String desc;
}
