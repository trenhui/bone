package com.bone.lowcode.infra.application.vo.upload;

import lombok.Data;

@Data
public class UploadAttachmentVO {

    private Long id;

    /**
     * 导入标题名称
     */
    private String title;

    /**
     * 导入类型,1:数据,2:影像件,3:图片,4:附件
     */
    private Byte dataType;

    /**
     * 单个附件大小上限
     */
    private Integer singleMaxSize;

    /**
     * 限定文件格式
     */
    private String fileFormat;

    /**
     * 单次数量限定
     */
    private Integer maxCount;

    /**
     * 导入操作说明
     */
    private String importDescription;
}
