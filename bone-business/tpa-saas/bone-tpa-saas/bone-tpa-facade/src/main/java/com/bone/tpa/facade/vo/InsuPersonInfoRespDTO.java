package com.bone.tpa.facade.vo;

import lombok.Data;

@Data
public class InsuPersonInfoRespDTO {

//    @ApiModelProperty(value = "姓名")
    private String personName;

//    @ApiModelProperty(value = "证件号")
    private String personCertId;

//    @ApiModelProperty(value = "证件类型")
    private String personCertType;
}
