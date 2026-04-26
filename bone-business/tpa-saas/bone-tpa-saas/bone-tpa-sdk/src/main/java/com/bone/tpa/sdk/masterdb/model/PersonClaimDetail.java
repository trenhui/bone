package com.bone.tpa.sdk.masterdb.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@TableName("tpa_person_claim_detail")
@Data
public class PersonClaimDetail implements Serializable {

    @TableId(value = "pclaim_detail_id", type = IdType.AUTO)
    private Long pclaimDetailId;

    @TableField("pclaim_detail_code")
    private String detailCode;

    @TableField("pclaim_code")
    private String claimCode;

    @TableField("pclaim_detail_imgUrl")
    private String detailImgUrl;

    @TableField("pclaim_detail_status")
    private Integer detailStatus;

    @TableField("pclaim_detail_personCode")
    private String detailPersonCode;

    @TableField("pclaim_detail_cardCode")
    private String detailCardCode;

    @TableField("pclaim_detail_updateTime")
    private Date updateTime;

    @TableField("pclaim_detail_updateShow")
    private Boolean updateShow;

    @TableField("pclaim_detail_updateUser")
    private String updateUser;

    /**
     * 图片类型
     */
    @TableField("pclaim_detail_imageType")
    private String detailImageType;

    /**
     * 是否被删除,0未删除,1删除
     */
    @TableField("pclaim_detail_imageDeleted")
    private Integer deleted;

    /**
     * 备注
     */
    @TableField("pclaim_detail_imageComment")
    private String comment;

    /**
     * 影像件是否首次提交, 1:首次提交,  0:非首次提交
     */
    @TableField("pclaim_detail_first_comment")
    private Integer firstComment;
}
