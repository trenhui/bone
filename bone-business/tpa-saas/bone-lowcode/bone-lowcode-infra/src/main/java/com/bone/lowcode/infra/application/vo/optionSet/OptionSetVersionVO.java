package com.bone.lowcode.infra.application.vo.optionSet;

import lombok.Data;

import java.util.Date;

@Data
public class OptionSetVersionVO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 选项集名称
     */
    private String optionSetName;

    /**
     * 版本描述
     */
    private String description;

    /**
     * 当前版本
     */
    private String version;

    /**
     * 创建人
     */
    private Long createBy;

    /**
     * 创建时间
     */
    private Date createTime;
}
