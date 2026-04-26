package com.bone.tpa.facade.request;

import lombok.Data;

import java.util.List;

@Data
public class OptionWithCnQueryRequest {
    /**
     *  因为yapi实现不了对数组的mock，所以这里用字符串
     */
    private String modelCodeMockTag ;

    /**
     * 业务身份编码
     */
    private String bizIdentityCode ;
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
     * 选项中文
     */
    private String optionCn;
}
