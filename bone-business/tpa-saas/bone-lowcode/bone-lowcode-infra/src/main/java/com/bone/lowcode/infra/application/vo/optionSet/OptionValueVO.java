package com.bone.lowcode.infra.application.vo.optionSet;

import lombok.Data;

@Data
public class OptionValueVO {

    private String id;

    /**
     * 选项值标识
     */
    private String code;

    /**
     * 选项值名称
     */
    private String name;

    /**
     * 选项值额外属性,json格式
     */
    private String extraProperty;

    /**
     * 选项值启用状态,0:不启用,1:启用
     */
    private Byte status;
}
