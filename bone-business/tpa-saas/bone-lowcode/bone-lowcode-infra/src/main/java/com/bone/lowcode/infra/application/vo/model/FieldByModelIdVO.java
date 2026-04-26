package com.bone.lowcode.infra.application.vo.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class FieldByModelIdVO {

    private String id;

    private String fieldName;

    private String fieldCode;

    private String componentType;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}
