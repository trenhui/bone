package com.bone.tpa.claim.application.request;

import lombok.Data;

@Data
public class UploadImageVO extends BaseImportVO {

    /**
     * 影像压缩包格式描述
     */
    private String packageDescription;

    /**
     * 包文件夹上限
     */
    private Integer folderMaxSize;

    /**
     * 同名文件处理方式,1:新旧都保留,2：覆盖原来的,仅保留新的
     */
    private Byte sameFileHandle;

    /**
     * 导入操作说明
     */
    private String importDescription;
}
