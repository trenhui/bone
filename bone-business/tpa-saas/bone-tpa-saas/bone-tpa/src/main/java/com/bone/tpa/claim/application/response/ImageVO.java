package com.bone.tpa.claim.application.response;

import lombok.Data;

@Data
public class ImageVO {

    private String id;

    /**
     * 影像件路径
     */
    private String imagePath;

    /**
     * 影像件名称
     */
    private String imageName;

    /**
     * 影像件类型(保司分类)
     */
    private String imageType;

    /**
     * 影像件类型(普康)
     */
    private String imagePkType;
}
