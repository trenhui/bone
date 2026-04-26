package com.bone.lowcode.infra.application.dto.upload;

import lombok.Data;

import java.util.List;

@Data
public class UpdateUploadImageDTO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 导入标题名称
     */
    private String title;

    /**
     * 影像压缩包格式描述
     */
    private String packageDescription;

    /**
     * 文件大小上限
     */
    private Integer fileMaxSize;

    /**
     * 包文件夹上限
     */
    private Integer folderMaxSize;

    /**
     * 限定文件格式
     */
    private String fileFormat;

    /**
     * 支持导入方式,1:新增,2:替换
     */
    private List<Byte> importTypeList;

    /**
     * 同名文件处理方式,1:新旧都保留,2：覆盖原来的,仅保留新的
     */
    private Byte sameFileHandle;

    /**
     * 导入操作说明
     */
    private String importDescription;
}
