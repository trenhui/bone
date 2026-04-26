package com.bone.tpa.claim.application.dto;

import lombok.Data;

/**
 * 影像件保司分类DTO
 */
@Data
public class ImageTypeDTO {
    /**
     * 分类名称
     */
    private String classifyName;
    /**
     * 保险公司分类code
     */
    private String classifyCode;

    /**
     * 普康分类code
     */
    private String classifyPkCode;
}
