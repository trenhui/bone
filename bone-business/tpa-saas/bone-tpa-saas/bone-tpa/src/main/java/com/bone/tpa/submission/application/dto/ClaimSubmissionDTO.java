package com.bone.tpa.submission.application.dto;

import com.bone.core.domain.extension.ExtensibleObject;
import com.bone.tpa.claim.application.dto.ClaimStakeholderDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class ClaimSubmissionDTO extends ExtensibleObject<ClaimSubmissionDTO, Long> {

    @Schema(description = "理赔申请唯一标识")
    private Long id;

    @Schema(description = "赔案编号")
    private String claimNo;

    @Schema(description = "申请人姓名")
    private String applicantName;

    @Schema(description = "申请人证件类型")
    private String applicantIdType;

    @Schema(description = "申请人证件号码")
    private String applicantIdNumber;

    @Schema(description = "保单编号")
    private String policyNo;

    @Schema(description = "事故/事件发生日期")
    private Date incidentDate;

    @Schema(description = "申请的理赔金额")
    private Double requestedAmount;

    @Schema(description = "理赔类型（如住院、门诊等）")
    private String claimType;

    @Schema(description = "影像件列表")
    private List<ClaimDocumentDTO> claimDocuments;

    @Schema(description = "提交渠道 (如: 移动应用、网页、FTP、API)")
    private String submissionChannel;

    @Schema(description = "状态")
    private String status; // 状态

    @Schema(description = "是否通过OCR增强处理")
    private Boolean ocrEnhanced;

    @Schema(description = "是否生成了自定义表单")
    private Boolean customFormGenerated;

    /**
     * 以下是非赔案维度的信息
     */
    @Schema(description = "出险人信息")
    private ClaimStakeholderDTO outInsurePerson;

    @Schema(description = "出险时间")
    private Date outInsureTime;
    @Schema(description = "出险类型")
    private String outInsureType;
    @Schema(description = "出险地址")
    private String outInsureAddress;

    @Schema(description = "被保人信息")
    private ClaimStakeholderDTO mainInsurePerson;
    @Schema(description = "受益人信息")
    private List<ClaimStakeholderDTO> benefitPerson = new ArrayList<>();

    @Schema(description = "领款人信息")
    private ClaimStakeholderDTO collectPerson;
}
