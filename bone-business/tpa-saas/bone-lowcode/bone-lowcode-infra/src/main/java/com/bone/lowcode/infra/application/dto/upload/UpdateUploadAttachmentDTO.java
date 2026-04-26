package com.bone.lowcode.infra.application.dto.upload;

import lombok.Data;

@Data
public class UpdateUploadAttachmentDTO {

    private Long id;


    /**
     * 导入标题名称
     */
    private String title;

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
