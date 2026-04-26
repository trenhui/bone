package com.bone.tpa.facade.request;

import lombok.Data;

import java.util.List;

@Data
public class OptionWithCodeQueryRequest {


    /**
     * 业务身份编码
     */
    private String bizIdentityCode ;

    private String modelCodeMockTag;
    /**
     * 模块内fieldCode
     *
     */
    private String fieldCode ;
    /**
     * 所属的模块code
     */
    private List<String> modeCodeList ;

    /**
     * 选项code
     */
    private String optionCode;

}
