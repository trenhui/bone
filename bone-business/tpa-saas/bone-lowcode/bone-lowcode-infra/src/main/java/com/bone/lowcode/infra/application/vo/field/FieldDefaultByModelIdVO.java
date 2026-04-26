package com.bone.lowcode.infra.application.vo.field;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class FieldDefaultByModelIdVO {

    private String id;

    private String modelName;

    private String fieldName;

    private String fieldCode;

    private Byte displayed; //0：隐藏，1：展示

    private Byte required; //0：不必填，1：必填

    private String dataBinding;

    private String componentType;

    private Integer sequence;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String createBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    private String updateBy;

}
