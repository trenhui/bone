package com.bone.tpa.claim.application.dto;

import com.bone.core.domain.extension.ExtensibleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@Schema(name = "claimDetail", description = "赔案信息")
public class ClaimDTO extends ExtensibleObject<ClaimDTO, Long> {

    /**
     * 必须赋值的对象，赔案基础信息
     */
    @Schema(description = "赔案号")
    private String claimNo;
    @Schema(description = "赔案uuid")
    private String claimDetailUuid;
    @Schema(description = "批次号")
    private String batchNo;
    @Schema(description = "个单号")
    private String slipPersonPsc;
    @Schema(description = "保全号")
    private String serialNumber;
    @Schema(description = "流程来源")
    private Integer signSystemSource;

    @Schema(description = "当前赔案状态")
    private String status;
    @Schema(description = "当前赔案阶段")
    private String stage;
    @Schema(description = "重要紧急")
    private String emergency;
    @Schema(description = "赔案来源")
    private String source;

    @Schema(description = "业务类型")
    private String bizType;
    @Schema(description = "流程类型")
    private String processType;
    @Schema(description = "严重等级")
    private Integer severenessLevel;

    /**
     * 保单信息
     */

    @Schema(description = "保单号")
    private String policyNo;
    @Schema(description = "保单开始日期")
    private String policyStartDate;
    @Schema(description = "保单结束日期")
    private String policyEndDate;
    @Schema(description = "保单属性code")
    private String policyAttribute;

    /**
     * 保司相关信息
     */

    @Schema(description = "保险公司名称")
    private String insuranceName;
    @Schema(description = "保险分公司名称")
    private String branchName ;
    @Schema(description = "投保公司")
    private String insureName;

    @Schema(description = "保司报案号")
    private String insurerClaimNo;
    @Schema(description = "保司申请号")
    private String insurerRequestNo;
    @Schema(description = "保司批次号")
    private String insurerBatchNo;
    @Schema(description = "保司收单号")
    private String insurerReceiptNo;
    @Schema(description = "保司保单号")
    private String insurerPolicyNo;


    /**
     * 签收相关信息
     */
    @Schema(description = "签收方式")
    private String signType;
    @Schema(description = "签收渠道")
    private String signChannel;
    @Schema(description = "签收时间")
    private Date signTime;
    @Schema(description = "签收机构")
    private String signInstitution;
    @Schema(description = "签收操作人员")
    private String signOperator;


    /**
     * 影像件相关信息
     */
    @Schema(description = "清点张数")
    private BigDecimal countingResult;
    @Schema(description = "影像是否上传")
    private String imageUploadFlag;
    @Schema(description = "影像数量")
    private BigDecimal imageCount;
    @Schema(description = "影像上传次数")
    private BigDecimal imageUploadCount;
    @Schema(description = "影像上传时间")
    private Date imageUploadTime;
    @Schema(description = "影像操作人员")
    private String imageUploader;


    @Schema(description = "计划uuid")
    private String planUuid;
    @Schema(description = "计划版本")
    private String planVersion;

    @Schema(description = "挂起状态")
    private String hangUpStatus;
    @Schema(description = "挂起类型code")
    private String hangUpType ;

    @Schema(description = "VIP级别")
    private String vipSign;
    @Schema(description = "VIP级别中文")
    private String vipSignCn;

    @Schema(description = "渠道名称code")
    private String sourceCode ;
    @Schema(description = "渠道名称中文")
    private String sourceCodeCn;


    /**
     * 操作人员信息
     */

    @Schema(description = "操作组id")
    private String operatorOrgId;
    @Schema(description = "操作组名")
    private String operatorOrgName;
    @Schema(description = "操作用户id")
    private String operatorUserId;
    @Schema(description = "操作用户名")
    private String operatorUserName;

    @Schema(description = "时效")
    private BigDecimal limitHour;

    @Schema(description = "初审人员")
    private String preExamOperatorName;
    @Schema(description = "签收确认日期")
    private Date signPassTime;
    @Schema(description = "最晚结案日期")
    private Date finishTime;
    @Schema(description = "初审开始时间")
    private Date preExamStartTime;
    @Schema(description = "初审确认时间")
    private Date preExamPassTime;

    @Schema(description = "赔案状态")
    private String claimStatus;

    /**
     * 列表需要有的字段
     */
    @Schema(description = "出险人")
    private String outInsureName;
    @Schema(description = "主被保人")
    private String mainInsureName;

//    @Schema(description = "复核驳回原因")
    private String rejectReason;

//    @Schema(description = "退回原因")
    private String returnReason;

//    @Schema(description = "登录账号")
    private String loginAccount;

//    @Schema(description = "当前作业人员")
    private String currentOperatorName;

//    @Schema(description = "各环节作业人员")
    private List<String> operatorNames;

//    @Schema(description = "挂起总时长")
    private BigDecimal hangUpTotalHour;


    private Map<String,Object> extraStore;

}
