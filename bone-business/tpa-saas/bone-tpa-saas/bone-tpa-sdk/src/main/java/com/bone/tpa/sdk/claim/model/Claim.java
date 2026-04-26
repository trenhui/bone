package com.bone.tpa.sdk.claim.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Date;

/**
 * ss_claim DO
 *
 * @author 0
 */
@Table("ss_claim")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class Claim extends ExtraStoreBase<Long> {

    /**
     * 赔案号
     */
    private String claimNo;

    /**
     * 赔案唯一的uuid
     */
    private String claimDetailUuid;


    /**
     * 关联表id
     */
    private Long relatedId;
    /**
     * 批次号
     */
    private String batchNo;

    /**
     * 流程来源
     *  0 saas 1 tpa
     */
    private Integer signSystemSource;
    /**
     * 保单号
     */
    private String policyNo;

    /**
     * 投保序号(保全号 )
     */
    private String serialNumber;

    /**
     * 个单号
     */
    private String slipPersonPsc;

    /**
     * 计划uuid
     */
    private String planUuid;

    /**
     * 当前状态
     */
    private String status;

    /**
     * 子状态
     */
    private String statusSub;
    /**
     * 当前阶段
     */
    private String stage;


    /**
     * 挂起状态
     */
    private String hangUpStatus;

    /**
     * 挂起类型code
     */
    private String hangUpType ;

    /**
     * 驳回类型
     */
    private String rejectType;

   /* *//**
     * 流程类型 code
     * 02半流程,01全流程,04录审分离
     * 见枚举 ClmProcessType
     *//*
    private String cfgClmProcess;*/
    /**
     * 影像件是否分类
     */
    private Integer cfgImageClassify;

    /**
     * 初审赔案类型 0-线上，1-线下 2-线上&线下
     */
    private Integer cfgClaimAuditType;

    /**
     * 是否需要初审
     */
    private Integer cfgNeedPreApprove;

    /**
     * 作业类型，1：新tpa，
     * 2：SAAS版仅录入 ，就是负责 初审，录入，质检
     */
    private Integer cfgBizType; //暂未消费

    /**
     * 电票需验真
     */
    private Integer cfgEinvoiceVerify; //暂未消费

    /**
     * 业务录入类型，--1:发票层，2：发票层和项目层
     * 见枚举 CfgBizInputTypeEnum
     */
    private Integer cfgBizInputType;

    /**
     * 自动化作业类型，1：自动化作业，2：非自动化作业
     */
    private Integer cfgAutoType;

    /**
     * vip_sign 的选项集code
     */
    private String vipSign;


    private String vipSignCn;

    /**
     * 存储出险类型码值
     */
    private String outInsureType;

    /**
     * 存储出险类型中文
     */
    private String outInsureTypeCn;

    /**
     * 渠道名称code
     * 线上映射表
     * dh：鼎和 pkb：普康宝 pkh 或普康荟：普康荟
     * txf：太享福 ychgj：永诚好管家 ydca：英大长安 chinapost:中邮
     * 线下映射表： 0：上门签收 1：快递签收 2：医保取数
     */
    private String sourceCode ;

    /**
     *  sourceCode的中文描述
     */
    private String sourceCodeCn;

    /**
     * 保单开始日期
     */
    private String policyStartDate;
    /**
     * 保单结束日期
     */
    private String policyEndDate;

    /**
     * 保单属性code，走枚举
     * SlipAttribute
     */
    private String policyAttribute;



    /**
     * 保险公司名称
     */
    private String insuranceName;

    /**
     * 保险分公司名称
     */
    private String branchName ;

    /**
     * 投保公司
     */
    private String insureName;
    /**
     * 重要紧急
     */
    private String emergency;
    /**
     * 赔案来源code
     *  类似： 见枚举 SourceType
     *
     */
    private String source;



    /**
     * 业务类型
     * 团险，个险的code
     * 见枚举 PolicyType
     */
    private String bizType;

    /**
     * 流程类型，存储的是流程的中文
     * 半流程，全流程这种
     * code 存储在 cfgClmProcess
     */
    private String processType;
    /**
     * 严重等级
     */
    private Integer severenessLevel;
    /**
     * 清点张数
     */
    private BigDecimal countingResult;
    /**
     * 影像是否上传
     */
    private String imageUploadFlag;
    /**
     * 影像数量
     */
    private BigDecimal imageCount;
    /**
     * 影像上传次数
     */
    private BigDecimal imageUploadCount;
    /**
     * 影像上传时间
     */
    private Date imageUploadTime;
    /**
     * 影像操作人员
     */
    private String imageUploader;
    /**
     * 保司报案号
     */
    private String insurerClaimNo;
    /**
     * 保司申请号
     */
    private String insurerRequestNo;
    /**
     * 保司批次号
     */
    private String insurerBatchNo;
    /**
     * 保司收单号
     */
    private String insurerReceiptNo;
    /**
     * 保司保单号
     */
    private String insurerPolicyNo;
    /**
     * 出险时间
     */
    private Date outInsureTime;

    /**
     * 出险地址省市区
     */
    private String outInsureRegion;

    /**
     * 出险地点
     */
    private String outInsureAddress;

    /**
     * 保单关系
     */
    private String policyRelation;

    /**
     * 关系类型
     */
    private Integer relationType;

    /**
     * 领款人类型
     */
    private String collectType;

    /**
     * 普康推送状态
     */
    private String pkPushStatus;

    /**
     * 普康推送时间
     */
    private Date pkPushTime;

    /**
     * 保司推送状态
     */
    private String insurancePushStatus;

    /**
     * 保司推送时间
     */
    private Date insurancePushTime;

    /**
     * 保司推送回调成功时间
     */
    private Date insuranceCallbackSuccessTime;

    /**
     * 保司推送回调原因
     */
    private String pushBackReason;

    /**
     * 错误归档
     */
    private String errorType;

    /**
     * 签收完成时间
     */
    private Date signPassTime;

    /**
     * 签收操作人员
     */
    private String signOperatorName;

    /**
     * 初审完成时间
     */
    private Date preExamPassTime;

    /**
     * 初审操作人员
     */
    private String preExamOperatorName;

    /**
     * 录入完成时间
     */
    private Date submittingPassTime;

    /**
     * 录入操作人员
     */
    private String submittingOperatorName;

    /**
     * 质检完成时间
     */
    private Date inspectionPassTime;

    /**
     * 质检操作人员
     */
    private String inspectionOperatorName;

    /**
     * 审核完成时间
     */
    private Date auditingPassTime;

    /**
     * 审核操作人员
     */
    private String auditingOperatorName;

    /**
     * 复核完成时间
     */
    private Date reviewingPassTime;

    /**
     * 复核操作人员
     */
    private String reviewingOperatorName;

    /**
     * 赔案时效(小时)
     */
    private BigDecimal limitHour;

    /**
     * 当前操作组id
     */
    private String operatorOrgId;

    /**
     * 当前操作组名
     */
    private String operatorOrgName;
    /**
     * 当前操作人id
     */
    private String operatorUserId;

    /**
     * 当前操作用户id
     */
    private String operatorUserName;


    private Long flowConfigId;
}
