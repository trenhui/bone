package com.bone.tpa.claim.application.response;

import com.bone.core.domain.extension.ExtensibleObject;
import com.bone.tpa.claim.application.dto.ClaimStakeholderDTO;
import com.bone.tpa.intelligent.adjustment.model.AdjustConclusion;
import com.bone.tpa.intelligent.adjustment.model.AdjustResult;
import com.bone.tpa.sdk.claim.model.Claim;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.data.annotation.Transient;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Schema(name = "claimDetail", description = "赔案信息")
public class ClaimDetailObject extends ExtensibleObject<Claim,Long> implements Serializable {

    /**
     * 主键id
     */
//    @Schema(description = "主键id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 提交操作类型
     */
//    @Schema(description = "操作类型")
    @Transient
    private String action;


    /**
     * 赔案基础信息
     */
    @Schema(description = "赔案号")
    private String claimNo;
    @Schema(description = "赔案uuid")
    private String claimDetailUuid;
    @Schema(description = "批次号")
    private String batchNo;
    @Schema(description = "流程来源", format = "SelectDrop")
    private String signSystemSource;

    @Schema(description = "当前赔案状态")
    private String status;
    @Schema(description = "当前赔案阶段")
    private String stage;
//    @Schema(description = "重要紧急")
//    private String emergency;
    @Schema(description = "赔案来源", format = "SelectDrop")
    private String source;

    @Schema(description = "保单号")
    private String policyNo;
    @Schema(description = "个单号")
    private String slipPersonPsc;
    @Schema(description = "保全号")
    private String serialNumber;
    @Schema(description = "保单开始日期")
    private String policyStartDate;
    @Schema(description = "保单结束日期")
    private String policyEndDate;
    @Schema(description = "保单属性", format = "SelectDrop")
    private String policyAttribute;

    @Schema(description = "计划Uuid")
    private String planUuid;

//    @Schema(description = "业务身份code")
    private String bizIdentityCode;
//    @Schema(description = "租户id")
    private Long tenantId;

    private Date createTime;

    private Date updateTime;


    /**
     * 签收相关信息
     */
    @Schema(description = "业务类型", format = "SelectDrop")
    private String bizType;
    @Schema(description = "流程类型", format = "SelectDrop")
    private String processType;

    @Schema(description = "严重等级")
    private Integer severenessLevel;

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

    @Schema(description = "挂起状态", format = "SelectDrop")
    private String hangUpStatus;
    @Schema(description = "挂起类型", format = "SelectDrop")
    private String hangUpType ;

    @Schema(description = "VIP级别", format = "SelectDrop")
    private String vipSign;
    @Schema(description = "VIP级别_中文")
    private String vipSignCn;

    @Schema(description = "渠道名称", format = "SelectDrop")
    private String sourceCode ;
    @Schema(description = "渠道名称_中文")
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

    /**
     * 时效
     */
    @Schema(description = "赔案时效")
    private BigDecimal limitHour;

    /**
     * 以下均为对应block的信息
     */
    @Schema(description = "出险人信息")
    private OutInsurePerson outInsurePerson;
    @Schema(description = "出险信息")
    private OutInsureInfo outInsureInfo;

    @Schema(description = "被保人信息")
    private MainInsurePerson mainInsurePerson;

//    @Schema(description = "受益人信息")
//    private List<BenefitPerson> benefitPeople = new ArrayList<>();

    @Schema(description = "领款人信息")
    private CollectInfo collectInfo;

//    @Schema(description = "领款人信息")
//    private List<CollectInfo> collectInfoList = new ArrayList<>();

//    @Schema(description = "保单信息")
//    private List<PolicyDTO> policyDTOList = new ArrayList<>();

//    @Schema(description = "理算过程信息")
//    private List<InvoiceAdjustmentResponse> adjustmentDetailList = new ArrayList<>();

    @Schema(description = "理算结果信息")
    private AdjustResult adjustmentResult;

    @Schema(description = "理算结论信息")
    private AdjustConclusion adjustConclusion;

   /* @Schema(description = "赔案专属字段")
    private Map<String, Object> extraProperties = new HashMap<>(64);
*/

    private Map<String,Object> extraStore;
    /**
     * 同步提示信息
     */
    private Map<String, String> syncHintMsg = new HashMap<>();

    @Schema(description = "复核驳回原因")
    private String rejectReason;

    @Schema(description = "退回原因")
    private String returnReason;

    @Schema(description = "登录账号")
    private String loginAccount;

    @Schema(description = "当前作业人员")
    private String currentOperatorName;

    //@Schema(description = "各环节作业人员")
    private List<String> operatorNames;

    @Schema(description = "挂起总时长")
    private BigDecimal hangUpTotalHour;
}
