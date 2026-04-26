package com.bone.tpa.facade.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * @description:
 * @author: caixuepu
 * @time: 2021/5/12
 */
@Data
public class PeopleInfoResponse {

    private Integer errorCode;
    private String errorMessage;
    private Integer code;
    private String message;
    private List<PeopleInfo> data;
}
