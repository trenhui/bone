package com.bone.lowcode.infra.application.vo.optionSet;

import lombok.Data;

import java.util.List;

@Data
public class OptionSetVO2 {

    /**
     * 选项集ID
     */
    private String id;

    /**
     * 选项集名称
     */
    private String setName;

    /**
     * 选项集标识
     */
    private String setCode;
}
