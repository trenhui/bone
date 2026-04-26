package com.bone.lowcode.infra.application.dto.processPage;

import com.bone.lowcode.infra.domain.model.PageHeadField;
import lombok.Data;

import java.util.List;

@Data
public class UpdateProcessDetailPageDTO {

    private Long id;

    /**
     * 页面介绍说明
     */
    private String description;

    /**
     * 是否开启页头,0:否,1:是
     */
    private Byte enablePageHead;

    //页头字段信息
    private List<PageHeadField> pageHeadFieldList;

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

}
