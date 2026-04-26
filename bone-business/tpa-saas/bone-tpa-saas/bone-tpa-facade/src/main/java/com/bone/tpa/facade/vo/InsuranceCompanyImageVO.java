package com.bone.tpa.facade.vo;

import lombok.Data;

@Data
public class InsuranceCompanyImageVO {
    private Integer id;
    /**
     * 保司
     */
    private String InsuranceCompanyId;
    /**
     * 分类名称
     */
    private String fieldName;
    /**
     * 保险公司分类code
     */
    private String imageClassifyCode;
    /**
     * 对应普康的分类code
     */
    private String imageMapCode;
    /**
     * 备注
     */
    private String remark;

    private Byte isDeleted;
}
