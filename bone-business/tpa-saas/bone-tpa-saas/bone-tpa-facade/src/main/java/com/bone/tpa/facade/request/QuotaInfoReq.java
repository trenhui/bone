package com.bone.tpa.facade.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class QuotaInfoReq {

    /**
     * 操作类型 1：扣减；2：退款(要求理赔金额汇总，不可传明细进行扣减，不允许部分退款)
     */
    @NotNull
    private Integer changType;

    /**
     * 被保险人和使用人信息
     */
    @NotNull
    private String personName;
    @NotNull
    private String personType;
    @NotNull
    private String personNumber;
    @NotNull
    private String userName;
    @NotNull
    private String userType;
    @NotNull
    private String userNumber;
    @NotNull
    private String relation;
    /**
     * 使用时间
     */
    @NotNull
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date userTime;

    /**
     * 订单号/赔案号
     */
    @NotNull
    private String businessNo;
    /**
     * 报案号
     */
    @NotNull
    private String reportNumber;

    /**
     * 备注
     */
    private String remark;


    /**
     * 退款必传
     * 01 用户取消订单
     * 02 TPA取消订单
     * 03 理算错误
     * 04 核赔退回
     * 99 其他
     */
//    @ApiModelProperty(value = "退款必传")
    private String refundReason;

//    @ApiModelProperty(value = "退款原因为99时，退款原因详述必录")
    private String refundReasonDesc;

    //发票详情
//    @ApiModelProperty(value ="发票详情")
    private List<ClaimDetail> claimDetails;

}
