package com.bone.lowcode.infra.application.vo.upload;

import lombok.Data;

@Data
public class UploadComponentVO {

    private String id;

    /**
     * 适用页面
     */
    private String applicablePage;

    /**
     * 导入标题名称
     */
    private String title;

    /**
     * 导入类型,1:数据,2:影像件,3:图片,4:附件
     */
    private Byte dataType;
}
