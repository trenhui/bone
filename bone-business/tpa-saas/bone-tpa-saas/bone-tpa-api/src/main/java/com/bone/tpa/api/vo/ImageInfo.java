package com.bone.tpa.api.vo;

import lombok.Data;

@Data
public class ImageInfo {

    /**
     * 图片的uuid
     * not null 必填
     */
    private String  imageUuid;

    /**
     * 图片名称
     * not null 必填
     */
    private  String imageName;

    /**
     * 图片存储地址
     * not null
     */
    private String   imagePath;

    /**
     * 影响件全局地址
     * not null
     */
    private Integer imageIndex;

    /**
     * 保险公司分类code
     * 如果传入null，则清空字段
     */
    private String imageType;

    /**
     * 普康分类
     */
    private String imagePkType ="1";




    /**
     * 影响件来源
     * 0 tpa
     * 1 saas
     * 只插入时候会设置，更新不设置
     */
    private Integer   source;

    /**
     * 是否被ocr识别过
     * 0 没有
     * 1 识别过了
     * 如果传入null，则清空字段
     */
    private Integer ocrDealedFlag;


    /**
     * 影像件是否推送标识(默认1-推送 0-不推送)
     */
    private Integer pushFlag;

}
