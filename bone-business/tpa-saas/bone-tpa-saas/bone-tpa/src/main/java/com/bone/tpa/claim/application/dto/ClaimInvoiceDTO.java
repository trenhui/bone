package com.bone.tpa.claim.application.dto;

import com.alibaba.fastjson.JSONObject;
import com.bone.core.domain.extension.ExtensibleObject;
import com.bone.tpa.intelligent.adjustment.model.LiabilityToBind;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Schema(name = "claimInvoice", description = "发票信息")
public class ClaimInvoiceDTO extends ExtensibleObject<ClaimInvoiceDTO,Long> {

//    @Schema(description = "关联赔案id")
    private Long relatedId;

    @Schema(description = "发票uuid")
    private String invoiceUuid;

    @Schema(description = "票据号码")
    private String invoiceNo;
    @Schema(description = "电子票据代码")
    private String claimInvoiceId;

    @Schema(description = "电子或纸质", format = "SelectDrop")
    private String paperType;

    @JsonProperty("eInvoiceNo")
    @Schema(description = "电子发票号")
    private String eInvoiceNo;

    @Schema(description = "票据验证码")
    private String verificationCode;

    @Schema(description = "开票日期")
    private Date invoiceDate;

    @Schema(description = "医疗发票类型", format = "SelectDrop")
    private String invoiceType;
    @Schema(description = "票据类型", format = "SelectDrop")
    private String billType;
    @Schema(description = "票据类型_中文")
    private String billTypeCn;
    @Schema(description = "发票姓名")
    private String invoiceName;

    @Schema(description = "录入方式")
    private String inputTypeCn;
    @Schema(description = "发票验真结果", format = "SelectDrop")
    private String verifyValid;
    @Schema(description = "发票验真次数")
    private Integer verifyValidTimes;

    @Schema(description = "就诊类型", format = "SelectDrop")
    private String visitType;

    @Schema(description = "就诊类型_中文")
    private String visitTypeCn;

    @Schema(description = "出险原因", format = "SelectDrop")
    private String outInsureReason;
    @Schema(description = "出险原因_中文")
    private String outInsureReasonCn;

    @Schema(description = "发票备注")
    private String remark;

    @Schema(description = "适用责任")
    private List<LiabilityToBind> relateLiability;
    @Schema(description = "医疗机构类型", format = "SelectDrop")
    private String medicalInstitutionType;
    @Schema(description = "医疗机构类型_中文")
    private String medicalInstitutionTypeCn;
    @Schema(description = "是否有医保", format = "SelectDrop")
    private String hasYb;
    @Schema(description = "医保类型", format = "SelectDrop")
    private String ybType;
    @Schema(description = "医保类型_中文")
    private String ybTypeCn;
    @Schema(description = "医院名称", format = "SelectDrop")
    private String hospitalCode;
    @Schema(description = "医院名称_中文")
    private String hospitalName;
    @Schema(description = "医院等级")
    private String hospitalLevel;
    @Schema(description = "医院性质")
    private String hospitalType;
    @Schema(description = "医院省市区")
    private String hospitalRegion;

    @Schema(description = "诊断", format = "SelectDrop")
    private String diagnosis;
    @Schema(description = "诊断_中文")
    private String diagnosisCn;
    @Schema(description = "是否重疾", format = "SelectDrop")
    private String severeFlag;
    @Schema(description = "重疾名称", format = "SelectDrop")
    private String severeNameCode;
    @Schema(description = "重疾名称_中文")
    private String severeName;
    @Schema(description = "是否慢病", format = "SelectDrop")
    private Integer chronicFlag;
    @Schema(description = "慢病名称", format = "SelectDrop")
    private String chronicCode;
    @Schema(description = "慢病名称_中文")
    private String chronicName;

    @Schema(description = "医院特征")
    private String hospitalProperties;
    @Schema(description = "就诊日期")
    private Date visitDate;
    @Schema(description = "住院期间")
    private String hospitalPeriod;
    @Schema(description = "住院科别", format = "SelectDrop")
    private String hospitalDepartment;
    @Schema(description = "住院科别_中文")
    private String hospitalDepartmentName;
    @Schema(description = "住院天数")
    private Integer hospitalDays;
    @Schema(description = "津贴天数")
    private Integer subsidyDays;

    @Schema(description = "发票总费用")
    private BigDecimal totalAmount;
    @Schema(description = "医保基金总支付")
    private BigDecimal totalMedicalFundPayment;
    @Schema(description = "其他基金金额") //??
    private BigDecimal otherFundAmount ;
    @Schema(description = "基本统筹金额")
    private BigDecimal basicPoolingAmount;
    @Schema(description = "统筹起付线")
    private BigDecimal poolingThreshold;
    @Schema(description = "统筹赔付比例")
    private BigDecimal poolingReimbursementRate;
    @Schema(description = "自付一")
    private BigDecimal selfPayPart1Amount;
    @Schema(description = "自付二")
    private BigDecimal selfPayPart2Amount;
    @Schema(description = "总自费金额")
    private BigDecimal totalSelfPayAmount;
    @Schema(description = "丙类自费")
    private BigDecimal classCSelfPayAmount;
    @Schema(description = "超限价自付")
    private BigDecimal excessLimitSelfPayAmount;
    @Schema(description = "第三方已赔付金额")
    private BigDecimal thirdPartyPaidAmount;
    @Schema(description = "合理费用金额")
    private BigDecimal validAmount;
    @Schema(description = "不合理费用金额")
    private BigDecimal invalidAmount;

    @Schema(description = "个人账户")
    private String accountNo;

    private  Map<String,Object>  extraStore;

    /**
     * 同步提示信息
     */
    private Map<String, String> syncHintMsg = new HashMap<>();



}
