package com.bone.tpa.claim.application.dto;

import lombok.Data;

import java.util.Date;

/**
 * tpa操作日志
 */
@Data
public class TpaLogDTO {

    private Long objectId;

    private String remark;

    private String operation;

    private String createBy;

    private Date createTime;

}
