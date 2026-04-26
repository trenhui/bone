package com.bone.tpa.claim.application.request;

import lombok.Data;

@Data
public class UploadPictureVO2 extends BaseImportVO {

    /**
     * 单张大小上限
     */
    private Integer singleMaxSize;

    /**
     * 上传张数限定
     */
    private Integer maxCount;

    /**
     * 导入操作说明
     */
    private String importDescription;
}
