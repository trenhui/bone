package com.bone.tpa.sdk.claim.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;

import java.util.Date;
import java.util.List;

/**
 * ss_sign_record DO
 *
 * @author 0
 */
@Table("ss_sign_record")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class SignRecord extends TenantAbstractEntity<Long> {

    /**
     * 批次号
     */
    private String batchNo;
    /**
     * 业务身份code
     */
    private String bizIdentityCode;
    /**
     * 签收方式
     */
    private String signType;
    /**
     * 签收渠道
     */
    private String signChannel;
    /**
     * 业务类型
     */
    private String bizType;
    /**
     * 流程类型
     */
    private String processType;
    /**
     * 赔案数
     */
    private Integer claimCount;
    /**
     * 是否有上传影像
     */
    private String imageUploadFlag;
    /**
     * 保险公司
     */
    private String insuranceCompany;
    /**
     * 保险分公司
     */
    private String insuranceSubsidiary;
    /**
     * 投保公司
     */
    private String insuringAgency;
    /**
     * 收单流水号
     */
    private String deliveryNo;
    /**
     * 收件时间
     */
    private Date deliveryTime;
    /**
     * 快递编号
     */
    private String expressNo;
    /**
     * 快递公司
     */
    private String expressCompany;
    /**
     * 快递到达时间
     */
    private Date arriveTime;
    /**
     * 发件地
     */
    private String sendFrom;
    /**
     * 发件人
     */
    private String sender;
    /**
     * 发件人联系方式
     */
    private String senderContact;
    /**
     * 紧急程度
     */
    private String emergency;
    /**
     * 备注
     */
    private String remark;
    /**
     * 人员清单
     */
    private String participant;
    /**
     * 签收时间
     */
    private Date signTime;
    /**
     * 签收机构
     */
    private String signInstitution;
    /**
     * 签收操作人员
     */
    private String signOperator;
    /**
     * 签收状态
     */
    private String signStatus;

    /**
     * 赔案列表
     */
    private List<Claim> claims;
}
