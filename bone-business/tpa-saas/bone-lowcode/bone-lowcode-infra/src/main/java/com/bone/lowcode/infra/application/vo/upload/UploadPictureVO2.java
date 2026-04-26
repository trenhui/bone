package com.bone.lowcode.infra.application.vo.upload;

import lombok.Data;

@Data
public class UploadPictureVO2 {

    /**
     * 主键ID
     */
    private String id;

    /**
     * 唯一编码
     */
    private String code;

    /**
     * 导入标题名称
     */
    private String title;

    /**
     * 导入类型,1:数据,2:影像件,3:图片,4:附件
     */
    private Byte dataType;

    /**
     * 单张大小上限
     */
    private Integer singleMaxSize;

    /**
     * 限定文件格式
     */
    private String fileFormat;

    /**
     * 上传张数限定
     */
    private Integer maxCount;

    /**
     * 导入操作说明
     */
    private String importDescription;
}
