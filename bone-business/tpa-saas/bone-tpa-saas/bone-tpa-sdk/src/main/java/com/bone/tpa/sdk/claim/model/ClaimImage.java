package com.bone.tpa.sdk.claim.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;


/**
 * ss_claim DO
 *
 * @author 0
 */
@Table("ss_claim_image")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ClaimImage extends TenantAbstractEntity<Long> {
    /**
     * 关联赔案id
     */
    private Long relatedId;

    /**
     * 影像件唯一键,uuid
     */
    private String imageDetailId;
    /**
     * 影像件排序
     */
    private Integer imageIndex;

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
    /**
     * 清晰类型
     */
    private Integer clearType;
    /**
     * 备注
     */
    private String remark;
    /**
     * 影像件是否被ocr处理
     * 0 没处理过 1 处理过了
     */
    private Integer ocrFlag;

    /**
     * 0 tpa 1 saas
     */
    private Integer sourceSystem ;
    /**
     * 影像件是否推送标识(默认1-推送 0-不推送)
     */
    private Integer pushFlag;

    /**
     * 单证类型,"0":"永诚_理赔结案通知书","1":"永诚_意健险理赔申请书"
     */
    private String certificateType;

    /**
     * 是否来自个人影像库,0:否,1:是
     */
    private Integer fromPersonalImage;
}
