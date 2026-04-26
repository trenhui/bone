package com.bone.lowcode.infra.application.vo.processPage;

import lombok.Data;

import java.util.List;

@Data
public class FirstAuditDetailPageVO {

    /**
     * 主键ID
     */
    private String id;

    /**
     * 详情页面名称
     */
    private String name;

    /**
     * 页面介绍说明
     */
    private String description;


    private ProcessPageHead pageHead;


    /**
     * 打开详情弹窗,0:否,1:是
     */
    private Byte openDetail;

    /**
     * 初审标准规范
     */
    private String specification;

    /**
     * 清晰提示文案
     */
    private String tip;

    /**
     * 影像清晰码值
     */
    private Byte imageQuality;

    /**
     * 影像分类码值
     */
    private Byte imageType;

    private List<String> modelNameList;
}
