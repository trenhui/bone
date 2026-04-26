package com.bone.lowcode.infra.application.vo.optionSet;

import lombok.Data;

import java.util.Date;

@Data
public class OptionSetVO {

    /**
     * 主键ID
     */
    private String id;

    /**
     * 适用范围
     */
    private Byte useScope;

    /**
     * 选项集名称
     */
    private String setName;

    /**
     * 选项集标识
     */
    private String setCode;

    /**
     * 选项集描述
     */
    private String setDesc;

    /**
     * 创建人
     */
    private Long createBy;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 修改人
     */
    private Long updateBy;

    /**
     * 修改时间
     */
    private Date updateTime;

    /**
     * 最新版本
     */
    private String lastVersion;
}
