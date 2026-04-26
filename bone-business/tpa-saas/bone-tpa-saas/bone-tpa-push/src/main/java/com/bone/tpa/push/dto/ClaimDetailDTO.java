package com.bone.tpa.push.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author feihaiming
 * @create 2025/10/16 15:04
 */
@Data
public class ClaimDetailDTO {
    // claim_detail
    private String adjustmentGuid;
    private String batchCode;
    private String groupPolicy;
    private String claimCode;
    private String corpCode;
    private String personCertId;
    private String billCode;
    private String visitDate;
    private String hospitalCode;
    private String hospitalName;
    private String visitDuty;
    private String diseaseId;
    private String diseaseName;
    private BigDecimal selfPayAmt = BigDecimal.ZERO;
    private BigDecimal classifyPay = BigDecimal.ZERO;
    private String nurseAmt;
    private BigDecimal selfCashAmt = BigDecimal.ZERO;
    private BigDecimal accountPayAmt = BigDecimal.ZERO;
    private BigDecimal planPayAmt;
    private BigDecimal thirdPartyPayAmt = BigDecimal.ZERO;
    private BigDecimal inspectAmt = BigDecimal.ZERO;
    private BigDecimal physiotherapyAmt = BigDecimal.ZERO;
    private BigDecimal medicineAmt = BigDecimal.ZERO;
    private BigDecimal cleanToothAmt = BigDecimal.ZERO;
    private String outDate;
    private BigDecimal hospitalDays = BigDecimal.ZERO;
    private Integer changeDays;
    private Integer abtmDays;
    private Integer compensateDays;
    private BigDecimal applyAmt = BigDecimal.ZERO;
    private BigDecimal abtmAmt = BigDecimal.ZERO;
    private BigDecimal compensateAmt = BigDecimal.ZERO;
    private String billProperty;
    private String unCompensateCause;
    private String enterDate;
    private String reCheckDate;
    private Integer classifyPayIsCompensate;
    private Integer selfPayIsCompensate;
    private String dutyId;
    private String diseaseCode;
    private Integer status;
    private BigDecimal unreasonableAmount = BigDecimal.ZERO;
    private BigDecimal reasonableAmount = BigDecimal.ZERO;
    private String hospitalGrade;
    private String hospitalLevel;
    private String hospitalIfYb;
    private String hospitalProperty;
    private String formulaText;
    private String formulaValue;
    private String earlyCompensateAmt;
    private String dutyName;
    private Integer visitType;
    private Integer haveYb;
    private String inHospitalDate;
    private Integer billType;
    private String hospitalProvince;
    private String hospitalCity;

    private List<ClaimDetailExtendDTO> claimDetailExtends;
    private List<ClaimProjectDTO> claimProjects;
    private List<ClaimDrugDTO> claimDrugs;

    // other
    private Map<String, String> extraFields = new HashMap<>();
}
