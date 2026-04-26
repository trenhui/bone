package com.bone.tpa.sdk.submission.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.domain.extension.ExtensibleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * ClaimSubmission 理赔申请模型
 */
@Table("ss_claim_submission")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ClaimSubmission extends ExtensibleObject<ClaimSubmission, Long> {

    @Schema(description = "理赔申请唯一标识")
    private Long id; // 理赔申请唯一标识

    @Schema(description = "赔案编号")
    private String claimNo; // 赔案编号

    @Schema(description = "申请人姓名")
    private String applicantName; // 申请人姓名

    @Schema(description = "申请人证件类型")
    private String applicantIdType; // 申请人证件类型

    @Schema(description = "申请人证件号码")
    private String applicantIdNumber; // 申请人证件号码

    @Schema(description = "保单编号")
    private String policyNo; // 保单编号

    @Schema(description = "事故/事件发生日期")
    private Date incidentDate; // 事故/事件发生日期

    @Schema(description = "申请的理赔金额")
    private Double requestedAmount; // 申请的理赔金额

    @Schema(description = "理赔类型（如住院、门诊等）")
    private String claimType; // 理赔类型（如住院、门诊等）

    @Schema(description = "提交渠道 (如: 移动应用、网页、FTP、API)")
    private String submissionChannel;

    @Schema(description = "状态")
    private String status; // 状态

    @Schema(description = "是否通过OCR增强处理")
    private Boolean ocrEnhanced;

    @Schema(description = "关联的影像件")
    private List<ClaimDocument> claimDocuments = new ArrayList<>(); // 关联的影像件
}
