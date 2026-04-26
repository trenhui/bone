package com.bone.tpa.facade.request;

import lombok.Data;

@Data
public class PolicySettingRemarkQueryRequest {


    private String mockTag ;
    /**
     * 身份证号
     */
    private String certId ;

    /**
     * 保单编号
     */
    private String policyNo ;
    /**
     * 0：特殊约定，1：特殊配置
     */
    private String settingSign= "0";
}
