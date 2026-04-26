package com.bone.tpa.facade.vo;

import lombok.Data;

/**
 * 基础的报案回执体
 */
@Data
public class CommonRegistResponse {

    //调用结果
    private Integer code;

    private String message;

    //保司报案号
    private String data;
}
