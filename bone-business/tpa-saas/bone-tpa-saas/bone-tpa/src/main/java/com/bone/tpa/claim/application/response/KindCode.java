package com.bone.tpa.claim.application.response;

import lombok.Data;

/**
 * 选项值
 */
@Data
public class KindCode {

    private String code;

    private String name;

    private String parentCode;



    public KindCode(String code, String name, String parentCode) {
        this.code = code;
        this.name = name;
        this.parentCode = parentCode;
    }
}
