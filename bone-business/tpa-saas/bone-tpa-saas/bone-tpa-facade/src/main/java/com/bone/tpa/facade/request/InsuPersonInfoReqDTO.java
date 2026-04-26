package com.bone.tpa.facade.request;

import lombok.Data;

import java.util.List;

@Data
public class InsuPersonInfoReqDTO {

//    @ApiModelProperty(value = "证件号")
    private List<String> personCertIdList;
}
