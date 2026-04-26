package com.bone.tpa.intelligent.adjustment.dto;

import lombok.Data;

import java.util.Date;

@Data
public class DtoBase {
    private Long id ;
    private Long tenantId ;
    private Date createTime;
    private Long createBy;
    private Date updateTime;
    private Long updateBy;
}
