package com.bone.tpa.push.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.bone.tpa.api.enums.EInvoiceValidResult;
import com.bone.tpa.api.enums.SourceType;
import com.bone.tpa.facade.vo.OptionSetDTO;
import com.bone.tpa.push.bean.BatchDataBean;
import com.bone.tpa.push.bean.InvoicePropertyBean;
import com.bone.tpa.push.bean.PushClaimContext;
import com.bone.tpa.push.constants.CommonConstant;
import com.bone.tpa.push.dto.*;
import com.bone.tpa.push.enums.*;
import com.bone.tpa.push.enums.GenderEnum;
import com.bone.tpa.push.feign.response.CompanyInfoResponse;
import com.bone.tpa.push.feign.response.PersonalImageResponse;
import com.bone.tpa.push.feign.response.SysDictDTO;
import com.bone.tpa.push.service.AbstractPackageClaimService;
import com.bone.tpa.push.service.LiabilityService;
import com.bone.tpa.push.service.VisitDutyStrategy;
import com.bone.tpa.push.util.*;
import com.bone.tpa.sdk.adjustment.enums.*;
import com.bone.tpa.sdk.adjustment.model.*;
import com.bone.tpa.sdk.adjustment.model.liability.AllowanceDetail;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.sdk.adjustment.model.liability.QuotaController;
import com.bone.tpa.sdk.adjustment.model.liability.RestrictOutInsure;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.*;
import com.bone.tpa.sdk.service.ClaimCopyLogBasicService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.bone.tpa.push.constants.CommonConstant.*;

/**
 * @Author feihaiming
 * @create 2025/10/20 11:29
 */
@Component("defaultPackageClaimService")
public class DefaultPackageClaimService extends AbstractPackageClaimService {

    @Autowired
    @Qualifier("pushLiabilityServiceImpl")
    private LiabilityService liabilityService;

    @Autowired
    private VisitDutyStrategyFactory visitDutyStrategyFactory;

    @Autowired
    private VisitTreatmentTypeStrategyFactory visitTreatmentTypeStrategyFactory;

    @Autowired
    private ClaimCopyLogBasicService copyLogBasicService;

    @Override
    public String getHandlerCode() {
        return CommonConstant.STANDARD_HANDLER_CODE;
    }

    @Override
    public void checkData(Long claimNo, PushClaimContext pushClaimContext) {

    }

    @Override
    public void packageClaimData(Long claimNo, PushClaimContext pushClaimContext, ClaimDetailPushDTO claimPushInfo) {
        Claim claim = pushClaimContext.getClaim();
        List<ClaimInvoice> invoiceList = pushClaimContext.getInvoiceList();
        List<AdjustmentRecord> adjustmentRecordList = pushClaimContext.getAdjustmentRecordList();
        AdjustmentResult adjustmentResult = pushClaimContext.getAdjustmentResult();
        Coverage coverage = pushClaimContext.getCoverage();
        Plan plan = pushClaimContext.getPlan();
        List<ClaimImage> claimImageList = pushClaimContext.getClaimImageList();
        SignRecord signRecord = pushClaimContext.getSignRecord();
        ClaimStakeholder outInsure = pushClaimContext.getOutInsure();
        ClaimStakeholder mainInsure = pushClaimContext.getMainInsure();
        ClaimStakeholder collectInsure = pushClaimContext.getCollectInsure();
        CompanyInfoResponse companyInfoResponse = pushClaimContext.getCompanyInfoResponse();
        OptionSetDTO mainOptionSet = pushClaimContext.getMainOptionSet();
        OptionSetDTO collectOptionSet = pushClaimContext.getCollectOptionSet();
        OptionSetDTO relationToMainInsureOptionSet = pushClaimContext.getRelationToMainInsureOptionSet();

        // 封装赔案信息
        ClaimDTO claimDTO = claimPushInfo.getClaim();
        claimDTO.setBatchCode("");
        claimDTO.setCaseStatus("2");
        claimDTO.setCaseStatusSecond("2-1");
        claimDTO.setCaseCode(claim.getClaimNo());
        BigDecimal amount = invoiceList.stream().map(invoice -> invoice.getValidAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        claimDTO.setPayMoney(amount);
        claimDTO.setReceivedName(collectInsure.getName());
        claimDTO.setReceivedBank(collectInsure.getBranchCodeCn());
        claimDTO.setReceivedBankNo(collectInsure.getAccountNo());
        claimDTO.setPayType("1");
        claimDTO.setCloseDate(DateUtil.formatDate(claim.getReviewingPassTime() == null ? claim.getAuditingPassTime(): claim.getReviewingPassTime()));
        String minHospitalDate = InvoiceTool.getMinHospitalDate(invoiceList.stream().filter(t -> StringUtils.isNotBlank(t.getHospitalPeriod())).map(invoice -> invoice.getHospitalPeriod()).collect(Collectors.toList()));
        claimDTO.setVisitDate(minHospitalDate);
        claimDTO.setName(outInsure.getName());
        claimDTO.setBbrzjh(outInsure.getIdentityNo());
        OptionSetUtil.getOptionSetValue(mainOptionSet, "setBbrzjlx", claimDTO, MAININSURE_IDENTITY_TYPE_SECOND_MAPPING_NAME);
        claimDTO.setZbbrzjh(mainInsure.getIdentityNo());
        claimDTO.setZbbrzjlx("a");
        claimDTO.setApplyTypeCode("CLAIM_APPLY_TYPE_MJZ");//申请类型代码
        claimDTO.setStatus(1);//数据状态
        claimDTO.setGroupPolicy(claim.getPolicyNo());//团单号,如果是拆分保单，则取原保单号,1.6不支持拆分保单

        claimDTO.setClaimCode(claim.getClaimNo());
        claimDTO.setClaimStatus(5);
        claimDTO.setCorpCode(claim.getSerialNumber());
        claimDTO.setPersonCertId(outInsure.getIdentityNo());
        claimDTO.setPolicy(claim.getSlipPersonPsc());
        claimDTO.setPolicyDate(minHospitalDate);
        claimDTO.setPolicyType(2);
        claimDTO.setAccidentNature("");
        claimDTO.setOutPass("2");
        claimDTO.setPayeeName(collectInsure.getName());
        claimDTO.setPayeeCertId(collectInsure.getIdentityNo());
        GenderEnum enumByCode = GenderEnum.getEnumByCode(collectInsure.getGender());
        claimDTO.setPayeeGender(ObjectUtil.defaultIfNull(enumByCode, t -> t.getInsureCode(), ""));
        claimDTO.setPersonMobile(collectInsure.getPhone());
        claimDTO.setPayeePayType("5");
        claimDTO.setPayeeAccount(collectInsure.getAccountNo());
        claimDTO.setBankCode(collectInsure.getBranchCode());
        BigDecimal totalAmount = invoiceList.stream().map(invoice -> invoice.getTotalAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        claimDTO.setApplyAmt(totalAmount);
        amount = adjustmentRecordList.stream().map(adjustmentRecord -> adjustmentRecord.getDeductAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        claimDTO.setAbtmAmt(amount);
        amount = adjustmentRecordList.stream().map(adjustmentRecord -> adjustmentRecord.getPayoutAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        claimDTO.setCompensateAmt(amount);
        amount = invoiceList.stream().map(invoice -> invoice.getInvalidAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        claimDTO.setChangeAmt(amount);
        claimDTO.setEndDate(DateUtil.formatDate(claim.getUpdateTime()));
        claimDTO.setTypeCode(coverage.getCoverageCode());
        claimDTO.setCorpName(claim.getInsureName());
        claimDTO.setMainPersonName(mainInsure.getName());
        claimDTO.setMainPersonCertId(mainInsure.getIdentityNo());
        claimDTO.setBankName(collectInsure.getBranchCodeCn());
        claimDTO.setAdjustmentGuid(UUID.randomUUID().toString());
        claimDTO.setBillAmt(totalAmount);
        claimDTO.setStatus(1);
        claimDTO.setImagingPath(claimImageList.stream().map(claimImage -> claimImage.getImagePath()).collect(Collectors.joining(";")));
        claimDTO.setTpaStatus(1);
        claimDTO.setPayStatus(1);
        claimDTO.setReportDate(DateUtil.formatDate(signRecord.getSignTime()));//调用报案的时间
        if (Objects.nonNull(companyInfoResponse)) {
            claimDTO.setBranchCode(companyInfoResponse.getCompanyNo());
        }
        claimDTO.setBranchName(claim.getBranchName());
        claimDTO.setSignDate(DateUtil.formatDate(signRecord.getSignTime()));//签收日期
        claimDTO.setPayeeCertBeginDate(DateTool.getFirstDateStr(collectInsure.getIdentityDatePeriod()));
        claimDTO.setPayeeCertEndDate(DateTool.getLastDateStr(collectInsure.getIdentityDatePeriod()));
        claimDTO.setOftenLiveAddress(collectInsure.getContactAddress());
        claimDTO.setPayObj(TransferPaymentMethodEnum.getValue(collectInsure.getTransferMethodType()));
        List<String> strings = adjustmentResult.getResultCode();
        if (CollectionUtil.isNotEmpty(strings)) {
            String str = strings.get(0);
            claimDTO.setConclusion(StringUtils.equals(str, "P") ? 1 : 3);
        }
        claimDTO.setUnCompensateCause(adjustmentResult.getResultDetail());
        claimDTO.setGuowangSerialNum(claim.getInsurerReceiptNo());//永诚无

        claimDTO.setMainPersonBegindate(DateTool.getFirstDate(mainInsure.getIdentityDatePeriod()));
        claimDTO.setMainPersonEnddate(DateTool.getFirstDate(mainInsure.getIdentityDatePeriod()));
        Integer copyFlag = 0;
        ClaimCopyLog claimCopyLog = copyLogBasicService.getClaimCopyLogByNewId(claim.getId());
        if (claimCopyLog != null) {
            copyFlag = 1;
        }
        claimDTO.setCopyCaseFlag(copyFlag);
        claimDTO.setReviewedBy(claim.getAuditingOperatorName());
        List<String> regions = CommonTool.getRegionCn(collectInsure.getBankRegion(), "desc");
        claimDTO.setPayeeBankProvince(regions.get(0));
        claimDTO.setPayeeBankCity(regions.get(1));
        OptionSetUtil.getOptionSetValue(collectOptionSet, "setPayeeCertType", claimDTO, COLLECTINSURE_IDENTITY_TYPE_MAIN_MAPPING_NAME);
        OptionSetUtil.getOptionSetValue(relationToMainInsureOptionSet, "setPayeeRelation", claimDTO, COLLECTINSURE_WITH_MAININSURE_RELATION_MAPPING_NAME);
        claimDTO.setPersonCertBeginDate(DateTool.getFirstDate(outInsure.getIdentityDatePeriod()));
        claimDTO.setPersonCertEndDate(DateTool.getLastDate(outInsure.getIdentityDatePeriod()));
        claimDTO.setPayeePhone(collectInsure.getPhone());
        regions = CommonTool.getRegionCn(claim.getOutInsureRegion(), "desc");
        claimDTO.setDangerPlace((regions.get(0) + "/" + regions.get(1) + "/" + regions.get(2)).replaceAll("/$", ""));
        regions = CommonTool.getRegionCn(claim.getOutInsureRegion(), "code");
        claimDTO.setDangerAreaCode((regions.get(0) + "/" + regions.get(1) + "/" + regions.get(2)).replaceAll("/$", ""));
        claimDTO.setZbRelation(claimDTO.getPayeeRelation());
        claimDTO.setPlanName(plan.getPlanName());
        claimDTO.setChannel(claim.getSourceCode());
        claimDTO.setImageIssTif((byte) 3);//saas是传3
        //永诚没有受益人信息，先注释
        claimDTO.setBeneRelation("");
//        OptionSetUtil.getOptionSetValue(relationToOutInsureOptionSet, "setBeneRelation", claimDTO, BENEINSURE_WITH_OUTINSURE_RELATION_MAPPING_NAME);
        //默认不处理
//        claimDTO.setBeneHolderRelation("");
//        claimDTO.setPayeeHolderRelation("");
        claimDTO.setBatchCodeTpa(claim.getBatchNo());
        claimDTO.setRelationType(claim.getRelationType());
        claimDTO.setBusinessMode(pushClaimContext.getPolicyConfig());
        claimDTO.setAgentType("");
        claimDTO.setIsFormalItiesComplete("");
        claimDTO.setIsIdentityCheck("");
        claimDTO.setPayThirdReason("");
        claimDTO.setReturnReason("");
        claimDTO.setCaseSource("normal");
        claimDTO.setCancelType("");
        claimDTO.setCancelReasonCode("");
        claimDTO.setSignTime(claim.getCreateTime());
        claimDTO.setVipLevel(StringUtils.isNotBlank(claim.getVipSign()) ? Integer.valueOf(claim.getVipSign()) : null);
        claimDTO.setDamageCode("");
        claimDTO.setRpDataState("3");
        claimDTO.setReviewFlag(pushClaimContext.getReviewFlag());
        claimDTO.setReviewMergeFlag(0);
        claimDTO.setReviewMergeParentCode("");
        claimDTO.setHandleType("");//todo
        claimDTO.setFreezeAmountParams("");//todo
        //end 赔案封装
    }

    @Override
    public void packageClaimImageData(Long claimNo, PushClaimContext pushClaimContext, ClaimDetailPushDTO claimPushInfo) {
        Claim claim = pushClaimContext.getClaim();
        List<ClaimImage> claimImageList = pushClaimContext.getClaimImageList();
        if (CollectionUtil.isEmpty(claimImageList)) {
            return;
        }
        List<PersonalImageResponse> personalClaimImageList = pushClaimContext.getPersonalClaimImageList();
        Map<String, String> imagePathToTypeMap;
        if (CollectionUtil.isNotEmpty(personalClaimImageList)) {
            imagePathToTypeMap = personalClaimImageList.stream().filter(r -> StringUtils.isNotEmpty(r.getImageType()) &&
                            StringUtils.isNotEmpty(r.getImagePath()))
                    .collect(Collectors.toMap(
                            PersonalImageResponse::getImagePath,
                            PersonalImageResponse::getImageType
                    ));
        } else {
            imagePathToTypeMap = new HashMap<>();
        }
        claimPushInfo.setImages(claimImageList.stream().map(claimImage -> {
            ClaimImageDTO claimImageInfo = new ClaimImageDTO();
            claimImageInfo.setClaimCode(claim.getClaimNo());
            claimImageInfo.setName(claimImage.getImageName());
            String imgType;
            if (CollectionUtil.isNotEmpty(imagePathToTypeMap)) {
                if (imagePathToTypeMap.containsKey(claimImage.getImagePath())) {
                    imgType = (StringUtils.isBlank(claimImage.getImagePath()) || StringUtils.isBlank(imagePathToTypeMap.get(claimImage.getImagePath()))) ? "" : imagePathToTypeMap.get(claimImage.getImagePath());
                } else {
                    imgType = "";
                }
            } else {
                imgType = "";
            }

            if (StringUtils.isNotBlank(imgType)) {
                claimImageInfo.setType(imgType);
            } else {
                if (StringUtils.isBlank(claimImage.getImageType())) {
                    throw new TpaBizException("图片类型不能为空");
                }

                //冗余字段
                claimImageInfo.getExtraFields().put(claimImage.getImagePath(), claimImage.getImageType());
            }
            claimImageInfo.setAddress(claimImage.getImagePath());
            claimImageInfo.setStatus(1);
            return claimImageInfo;
        }).collect(Collectors.toList()));
    }

    @Override
    public void packageClaimDetailData(Long claimNo, PushClaimContext pushClaimContext, ClaimDetailPushDTO claimPushInfo) {
        Claim claim = pushClaimContext.getClaim();
        ClaimDTO claimDTO = claimPushInfo.getClaim();
        List<ClaimInvoice> invoiceList = pushClaimContext.getInvoiceList();
        List<AdjustmentRecord> adjustmentRecordList = pushClaimContext.getAdjustmentRecordList();
        BatchDataBean batchDataBean = liabilityService.getLiabilityMap(invoiceList, adjustmentRecordList.get(0).getVersion());
        AdjustmentResult adjustmentResult = pushClaimContext.getAdjustmentResult();
        List<ClaimDetailDTO> details = claimPushInfo.getDetails();
        ClaimStakeholder outInsure = pushClaimContext.getOutInsure();
        ClaimStakeholder mainInsure = pushClaimContext.getMainInsure();
        ClaimStakeholder collectInsure = pushClaimContext.getCollectInsure();
        List<ClaimImage> claimImageList = pushClaimContext.getClaimImageList();
        Map<String, ClaimImage> claimImageMap = claimImageList.stream().collect(Collectors.toMap(ClaimImage::getImageDetailId, Function.identity(), (k1, k2) -> k1));
        List<InvoiceImageRelation> invoiceImageRelationList = pushClaimContext.getInvoiceImageRelationList();
        Map<String, List<InvoiceImageRelation>> invoiceImageMap = invoiceImageRelationList.stream().collect(Collectors.groupingBy(InvoiceImageRelation::getInvoiceUuid));
        List<InvoiceProject> invoiceProjectList = pushClaimContext.getInvoiceProjectList();
        List<InvoiceProjectItem> invoiceProjectItemList = pushClaimContext.getInvoiceProjectItemList();
        if (CollectionUtil.isEmpty(invoiceList)) {
            return;
        }

        List<LiabilityMapping> dutyConfigs = pushClaimContext.getDutyConfigList();
        String insuranceName = claim.getInsuranceName();
        String branchName = claim.getBranchName();
        boolean isRbsOrTbcClaim = SPECIAL_INSURANCE_NAMES.contains(insuranceName)
                || SPECIAL_BRANCH_NAMES.contains(branchName)
                || (insuranceName != null && insuranceName.contains(SIMPLE_DINGHE));
        VisitDutyStrategy strategy = visitDutyStrategyFactory.getStrategy(
                CollectionUtil.isNotEmpty(dutyConfigs), isRbsOrTbcClaim, RENSHOU.equals(insuranceName));

        details.addAll(invoiceList.stream().map(invoice -> {
            String liabilityUUID = InvoiceTool.getFirstLiabilityUUID(invoice.getRelateLiability());
            LiabilityConfig liabilityConfig = batchDataBean.getLiabilityMap().getOrDefault(liabilityUUID, new LiabilityConfig());
            Coverage coverage = batchDataBean.getCoverageMap().getOrDefault(liabilityConfig.getCoverageId(), new Coverage());

            ClaimDetailDTO claimDetailDTO = new ClaimDetailDTO();
            claimDetailDTO.setAdjustmentGuid(claimDTO.getAdjustmentGuid());
            claimDetailDTO.setBatchCode("");
            claimDetailDTO.setGroupPolicy(claimDTO.getGroupPolicy());
            claimDetailDTO.setClaimCode(claimDTO.getClaimCode());
            claimDetailDTO.setCorpCode(claimDTO.getCorpCode());
            claimDetailDTO.setPersonCertId(outInsure.getIdentityNo());
            claimDetailDTO.setBillCode(invoice.getInvoiceNo());
            claimDetailDTO.setVisitDate(claimDTO.getVisitDate());
            claimDetailDTO.setHospitalCode(StringUtils.isEmpty(invoice.getHospitalCode()) ? HOSPITAL_CODE_NOT_EXISTS : invoice.getHospitalCode());
            claimDetailDTO.setHospitalName(StringUtils.isEmpty(invoice.getHospitalName()) ? HOSPITAL_NAME_NOT_EXISTS : invoice.getHospitalName());
            String visitDutyName = strategy.calculateDuty(liabilityConfig, coverage, invoice, dutyConfigs);
            claimDetailDTO.setVisitDuty(visitDutyName);
            claimDetailDTO.setDiseaseId(invoice.getDiagnosis());
            claimDetailDTO.setDiseaseName(invoice.getDiagnosisCn());
            claimDetailDTO.setSelfPayAmt(invoice.getTotalSelfPayAmount());
            claimDetailDTO.setClassifyPay(invoice.getSelfPayPart2Amount());
            claimDetailDTO.setNurseAmt("0");
            claimDetailDTO.setSelfCashAmt(BigDecimal.ZERO);
            claimDetailDTO.setAccountPayAmt(BigDecimal.ZERO);
            claimDetailDTO.setPlanPayAmt(invoice.getTotalMedicalFundPayment());
            claimDetailDTO.setThirdPartyPayAmt(invoice.getThirdPartyPaidAmount());
            claimDetailDTO.setInspectAmt(BigDecimal.ZERO);
            claimDetailDTO.setPhysiotherapyAmt(BigDecimal.ZERO);
            claimDetailDTO.setMedicineAmt(BigDecimal.ZERO);
            claimDetailDTO.setCleanToothAmt(BigDecimal.ZERO);
            claimDetailDTO.setOutDate(DateTool.getLastDateStr(invoice.getHospitalPeriod()));
            visitTreatmentTypeStrategyFactory.getStrategy(invoice.getVisitType()).setClaimDetailFieldValue(invoice, claimDetailDTO);
            claimDetailDTO.setChangeDays(0);
            claimDetailDTO.setAbtmDays(0);
            if (invoice.getTotalAmount().compareTo(BigDecimal.ZERO) == 0) {
                claimDetailDTO.setApplyAmt(invoice.getInvalidAmount());
            } else {
                claimDetailDTO.setApplyAmt(invoice.getTotalAmount());
            }
            BigDecimal amt = adjustmentRecordList.stream().filter(t -> t.getInvoiceId().equals(invoice.getId())).map(AdjustmentRecord::getDeductAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            claimDetailDTO.setAbtmAmt(amt);
            amt = adjustmentRecordList.stream().filter(t -> t.getInvoiceId().equals(invoice.getId())).map(AdjustmentRecord::getPayoutAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            claimDetailDTO.setCompensateAmt(amt);
            if (invoice.getValidAmount().compareTo(BigDecimal.ZERO) > 0 || claimDetailDTO.getApplyAmt().compareTo(invoice.getValidAmount()) == 0 || invoice.getInvalidAmount().compareTo(claimDetailDTO.getApplyAmt()) < 0) {
                claimDetailDTO.setBillProperty("0");
            } else {
                claimDetailDTO.setBillProperty("2"); //拒赔
                claimDetailDTO.setUnCompensateCause(invoice.getRemark());//要传备注
            }
            claimDetailDTO.setEnterDate(DateUtil.formatDate(invoice.getCreateTime()));
            claimDetailDTO.setReCheckDate(DateUtil.formatDate(invoice.getUpdateTime()));
            claimDetailDTO.setClassifyPayIsCompensate(invoice.getSelfPayPart2Amount() != null && BigDecimal.ZERO.compareTo(invoice.getSelfPayPart2Amount()) < 0 ? 1 : 0);
            claimDetailDTO.setSelfPayIsCompensate(invoice.getTotalSelfPayAmount() != null && BigDecimal.ZERO.compareTo(invoice.getTotalSelfPayAmount()) < 0 ? 1 : 0);
            claimDetailDTO.setDutyId(InvoiceTool.getFirstLiabilityUUID(invoice.getRelateLiability()));
            claimDetailDTO.setDiseaseCode(invoice.getDiagnosis());
            claimDetailDTO.setStatus(1);
            claimDetailDTO.setUnreasonableAmount(invoice.getInvalidAmount());
            claimDetailDTO.setReasonableAmount(invoice.getValidAmount());
            claimDetailDTO.setHospitalGrade("2");
            claimDetailDTO.setHospitalLevel("0");
            claimDetailDTO.setHospitalIfYb(Objects.equals(invoice.getHasYb(), "1") ? "true" : "false");
            claimDetailDTO.setHospitalProperty("公立");
            claimDetailDTO.setFormulaText( liabilityConfig.getFormula() );
            String formula = adjustmentRecordList.stream()
                    .filter(t -> Objects.equals(t.getInvoiceId(), invoice.getId()))
                    .map(AdjustmentRecord::getFormula)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            claimDetailDTO.setFormulaValue( formula );
            claimDetailDTO.setEarlyCompensateAmt( Objects.isNull(invoice.getTotalMedicalFundPayment()) ? null : invoice.getTotalMedicalFundPayment().toString() );//统筹金额赋到这个值上面
            claimDetailDTO.setDutyName( liabilityConfig.getLiabilityName() );
            claimDetailDTO.setHaveYb( Objects.equals(invoice.getHasYb(), 1) ? 2 : 1 );
            claimDetailDTO.setInHospitalDate( DateTool.getFirstDateStr(invoice.getHospitalPeriod()) );
            claimDetailDTO.setBillType( StringUtils.isBlank(invoice.getBillType()) ? null : Integer.valueOf(invoice.getBillType()) );
            // 默认值
            List<String> regions = CommonTool.getRegionCn(invoice.getHospitalRegion(), "desc");
            claimDetailDTO.setHospitalProvince(regions.get(0));
            claimDetailDTO.setHospitalCity(regions.get(1));
            String[] dutyIds = invoice.getRelateLiability().split(",");
            List<ClaimDetailExtendDTO> itemS = Arrays.stream(dutyIds)
                    .map(part -> {
                        ClaimDetailExtendDTO item = new ClaimDetailExtendDTO();
                        item.setTpaDutyId(part);
                        item.setTreatmentType(invoice.getVisitType());
                        item.setSegmentIs(0L);
                        item.setSplitIs(dutyIds.length > 1 ? 1L : 0L);
//                        item.setAuditConclusion(2L);//永诚不需要
                        item.setBillRemark(invoice.getRemark());
                        strategy.setClaimDetailExtendFieldValue(item, liabilityConfig, coverage, invoice, dutyConfigs);
                        item.setExtendDutyName( liabilityConfig.getLiabilityName() );
                        AdjustmentRecord adjustmentRecord = adjustmentRecordList.stream()
                                .filter(t -> Objects.equals(t.getInvoiceId(), invoice.getId()) &&
                                        StringUtils.equals(t.getLiabilityUuid(), part))
                                .findFirst()
                                .orElse(AdjustmentRecord.builder().build());
                        item.setBillDutyCompensateAmt(ObjectUtil.defaultIfNull(adjustmentRecord, t -> t.getPayoutAmount(), BigDecimal.ZERO));
                        item.setBillMemo( adjustmentResult.getResultDetail() );
                        item.setDetailType(StringUtils.isNotBlank(adjustmentRecord.getCompensateType()) ? Integer.valueOf(adjustmentRecord.getCompensateType()) : null);
                        item.setBillName( invoice.getInvoiceName() );
                        List<InvoiceImageRelation> filterImageRelations = invoiceImageMap.getOrDefault(invoice.getClaimInvoiceId(), null);
                        if (Objects.nonNull(filterImageRelations)) {
                            List<String> filterImagePaths = filterImageRelations.stream().map(t -> {
                                ClaimImage claimImage = claimImageMap.get(t.getImageDetailId());
                                if (Objects.isNull(claimImage)) return null;
                                return claimImage.getImagePath();
                            }).collect(Collectors.toList());
                            item.setDetailImgPath( filterImagePaths.stream().filter(Objects::nonNull).collect(Collectors.joining(";")) );
                        }
                        BigDecimal billReasonableAmount = new BigDecimal(0.00);
                        String feePayoutList = adjustmentRecord.getFeePayoutList();
                        if (StringUtils.isEmpty(feePayoutList)) {
                            feePayoutList = "{}";
                        }
                        Map<String, BigDecimal> map = JSON.parseObject(feePayoutList, new TypeReference<>() {
                        });
                        BigDecimal temp = map.get(AmountNameEnum.合理金额.name());
                        if (Objects.nonNull(temp)) {
                            billReasonableAmount = billReasonableAmount.add(temp);
                            item.setMedicalSelfPayAmount( temp );//承担合理消费金额
                        }
                        temp = map.get(AmountNameEnum.总自费.name());
                        if (Objects.nonNull(temp)) {
                            billReasonableAmount = billReasonableAmount.add(temp);
                        }
                        temp = map.get(AmountNameEnum.自付二.name());
                        if (Objects.nonNull(temp)) {
                            billReasonableAmount = billReasonableAmount.add(temp);
                        }
                        item.setBillReasonableAmount( billReasonableAmount );
                        item.setBillApplyAmount( invoice.getTotalAmount() );
                        temp = map.get(AmountNameEnum.自付二.name());
                        if (Objects.nonNull(temp)) {
                            item.setPartSelfPayAmount( temp );
                        }
                        temp = map.get(AmountNameEnum.总自费.name());
                        if (Objects.nonNull(temp)) {
                            item.setAllSelfPayAmount( temp );
                        }
                        item.setMedicalPayAmount( BigDecimal.ZERO );
                        item.setCashPayAmount( BigDecimal.ZERO );
                        item.setItemKindCode( "" );
                        item.setItemKindName( "" );
                        String ratioPayoutList = adjustmentRecord.getRatioPayoutList();
                        BigDecimal firstRatio = JsonValueUtils.getFirstValue(ratioPayoutList);
                        item.setDutyCompensateRatio( firstRatio );
                        item.setThirdFeeCode( "" );
                        String feeCalculateList = adjustmentRecord.getFeeCalculateList();
                        if (StringUtils.isEmpty(feeCalculateList)) {
                            feeCalculateList = "{}";
                        }
                        Map<String, BigDecimal> feeCalculateMap = JSON.parseObject(feeCalculateList, new TypeReference<>() {
                        });
                        BigDecimal tempAmt = BigDecimal.ZERO;
                        if (CollectionUtil.isNotEmpty(feeCalculateMap)) {
                            for (Map.Entry<String, BigDecimal> entry : feeCalculateMap.entrySet()) {
                                if (Objects.isNull(entry.getValue())){
                                    continue;
                                }
                                tempAmt = tempAmt.add(entry.getValue());
                            }
                        }
                        item.setBillDutyCompensateAllAmt( tempAmt );
                        item.setAbtmAmt( adjustmentRecord.getDeductAmount() );
                        if (Objects.nonNull(invoice.getSubsidyDays())) {
                            item.setInvoiceAllowanceDays(new BigDecimal(invoice.getSubsidyDays()));
                        }
                        if (Objects.nonNull(adjustmentRecord.getDeductDays())) {
                            item.setResponsibilityDeductibleDays(new BigDecimal(adjustmentRecord.getDeductDays()));
                        }
                        AllowanceDetail allowanceDetail = liabilityConfig.getAllowanceDetail();
                        if (Objects.nonNull(allowanceDetail)) {
                            item.setDailyCompensationAmount(allowanceDetail.getAllowancePerDay());
                        }
                        item.setDutyGuid(part);
                        String eInvoiceNo = invoice.getEInvoiceNo();
                        if (StrUtil.isNotBlank(eInvoiceNo)) {
                            item.setIsElectronicInvoice(1);
                        } else {
                            item.setIsElectronicInvoice(0);
                        }
                        item.setPaperType(invoice.getPaperType());
                        item.setInvoiceCode(invoice.getClaimInvoiceId());//永诚北京才有
                        item.setInvoiceVerificationCode( invoice.getVerificationCode() );//永诚北京才有
                        item.setInvoiceDate( invoice.getInvoiceDate() );//永诚北京才有
                        item.setIsBjInvoice( 0 );//永诚北京才有
                        if (StringUtils.isNotBlank(invoice.getVerifyValid())) {
                            if (invoice.getVerifyValid().equals(EInvoiceValidResult.真票.getCode())) {
                                item.setIsVerifyValid(1);
                            } else {
                                item.setIsVerifyValid(0);
                            }
                        }
                        //默认值
                        item.setIsHistoryInjury( "0" );
                        item.setUnderMinimumAmt( BigDecimal.ZERO );
                        item.setUpperMinimumAmt( BigDecimal.ZERO );
//                        item.setCompLimitAmt( BigDecimal.ZERO );
                        item.setUpperCompLimitAmt( BigDecimal.ZERO );
                        item.setUnderMinimumPayAmt( BigDecimal.ZERO );
                        item.setUpperMinimumPayAmt( BigDecimal.ZERO );
                        item.setCompLimitPayAmt( BigDecimal.ZERO );
                        item.setUpperCompLimitPayAmt( BigDecimal.ZERO );
                        item.setArithmeticFormula(ObjectUtil.defaultIfNull(adjustmentRecord, t -> t.getFormula(), ""));
//                        item.setFinanceVoucherTypeCode( "" );
                        item.setIsCriticalIllness( invoice.getSevereFlag() );
                        item.setIsChronicDisease( invoice.getChronicFlag() );
//                        item.setOutpatientNo( "" );
//                        item.setMedicalRecordNo( "" );
//                        item.setInpatientNo( "" );
//                        item.setInpatientDepartment( "" );
                        item.setPrepaidAmt( BigDecimal.ZERO );
                        item.setRefundAmt( BigDecimal.ZERO );
                        item.setCriticalIllnessInsAmt( BigDecimal.ZERO );
                        item.setMedicalAssistAmt( BigDecimal.ZERO );
                        item.setCivilServantMedicalSubsidyAmt( BigDecimal.ZERO );
                        item.setMajorSupplementAmt( BigDecimal.ZERO );
                        item.setOtherAmt( BigDecimal.ZERO );
                        item.setIllnessDeathBenefitAmt( BigDecimal.ZERO );
                        item.setSelfPaidAmt( BigDecimal.ZERO );
                        item.setSelfFinanceAmt( BigDecimal.ZERO );
                        item.setBillRepetitionExplain( "" );
                        item.setIsPharmacyLicenseVerified( 0 );
                        return item;
                    })
                    .collect(Collectors.toList());
            claimDetailDTO.setClaimDetailExtends(itemS);
            if (CollectionUtil.isNotEmpty(invoiceProjectList)) {
                List<ClaimProjectDTO> claimProjectDTOs = invoiceProjectList.stream().map(projectInfo -> {

                    ClaimProjectDTO claimProjectDTO = new ClaimProjectDTO();

                    claimProjectDTO.setCostProjectCode(projectInfo.getProjectCode());
                    claimProjectDTO.setCostProject(projectInfo.getProjectName());
                    claimProjectDTO.setBillAmt(projectInfo.getInvoiceAmount());
                    claimProjectDTO.setSelfPayAmt(projectInfo.getProjectSelfPayAmount());
                    claimProjectDTO.setSelfCashAmt(projectInfo.getProjectPartSelfPayAmount());
                    claimProjectDTO.setPlanPayAmt(projectInfo.getPoolingAmount());
                    claimProjectDTO.setThirdPartyPayAmt(projectInfo.getProjectThirdPartyPayAmount());
                    claimProjectDTO.setDeductionRadio(BigDecimal.ZERO);//todo
                    claimProjectDTO.setDeductionAmount(BigDecimal.ZERO);//todo
                    claimProjectDTO.setRemark("");//todo
                    claimProjectDTO.setReasonableAmount(projectInfo.getInvoiceAmount().subtract(projectInfo.getProjectThirdPartyPayAmount()).subtract(projectInfo.getPoolingAmount())
                            .subtract(projectInfo.getProjectSelfPayAmount()).subtract(projectInfo.getProjectSelfPayAmount()).subtract(projectInfo.getProjectUnreasonableAmount()));
                    claimProjectDTO.setNotReasonableAmount(projectInfo.getProjectUnreasonableAmount());
                    claimProjectDTO.setSerialNo(projectInfo.getId());//todo

                    return claimProjectDTO;
                }).collect(Collectors.toList());
                claimDetailDTO.setClaimProjects(claimProjectDTOs);
            }

            if (CollectionUtil.isNotEmpty(invoiceProjectItemList)) {
                List<ClaimDrugDTO> claimDrugDTOS = invoiceProjectItemList.stream().map(invoiceProjectItem -> {

                    ClaimDrugDTO claimDrugDTO = new ClaimDrugDTO();

                    claimDrugDTO.setBillCode(invoiceProjectItem.getRelatedInvoiceNo());
                    claimDrugDTO.setGoodsName(invoiceProjectItem.getItemName());
                    claimDrugDTO.setDosageForm(invoiceProjectItem.getDosageFormCn());
                    claimDrugDTO.setCostProjectCode(invoiceProjectItem.getRelatedProjectCode());
                    claimDrugDTO.setCostProject(invoiceProjectItem.getRelatedProjectName());
                    claimDrugDTO.setNote("");//todo
                    claimDrugDTO.setYbType(invoiceProjectItem.getMedicalType());
                    if (Objects.nonNull(invoiceProjectItem.getCount())) {
                        claimDrugDTO.setCount(new BigDecimal(invoiceProjectItem.getCount()));
                    }
                    claimDrugDTO.setOnePrice(invoiceProjectItem.getPrice());
                    claimDrugDTO.setAmt(invoiceProjectItem.getItemTotalAmount());
                    if (Objects.nonNull(invoiceProjectItem.getChargingPercentage())) {
                        claimDrugDTO.setSincePayProportion(invoiceProjectItem.getChargingPercentage().toString());
                        claimDrugDTO.setDeductProportion(claimDrugDTO.getSincePayProportion());
                    }
                    claimDrugDTO.setSincePayAmt(invoiceProjectItem.getChargingAmount());
                    claimDrugDTO.setStatus(1);
                    claimDrugDTO.setDeductAmt(invoiceProjectItem.getChargingAmount());
                    claimDrugDTO.setThirdAmt(BigDecimal.ZERO);//todo
                    claimDrugDTO.setUnreasonableAmt(BigDecimal.ZERO);//todo
                    claimDrugDTO.setProjectId(invoiceProjectItem.getItemUuid());
                    claimDrugDTO.setPartSelfPayAmt(BigDecimal.ZERO);//todo
                    claimDrugDTO.setPlanPayAmt(BigDecimal.ZERO);//todo
                    claimDrugDTO.setCompensateAmt(BigDecimal.ZERO);//todo
                    claimDrugDTO.setExclusionAmt(BigDecimal.ZERO);//todo
                    claimDrugDTO.setMedicalInsurance(0);//todo

                    return claimDrugDTO;
                }).collect(Collectors.toList());
                claimDetailDTO.setClaimDrugs(claimDrugDTOS);
            }
            return claimDetailDTO;
        }).collect(Collectors.toList()));
    }

    @Override
    public void packageClaimConclusionData(Long claimNo, PushClaimContext pushClaimContext, ClaimDetailPushDTO claimPushInfo) {
        List<ClaimConclusionDTO> conclusions = claimPushInfo.getConclusions();
        Claim claim = pushClaimContext.getClaim();
        Policy policy = pushClaimContext.getPolicy();
        List<ClaimInvoice> invoiceList = pushClaimContext.getInvoiceList();
        List<AdjustmentRecord> adjustmentRecordList = pushClaimContext.getAdjustmentRecordList();
        BatchDataBean batchDataBean = liabilityService.getLiabilityMap(invoiceList, adjustmentRecordList.get(0).getVersion());
        AdjustmentResult adjustmentResult = pushClaimContext.getAdjustmentResult();
        ClaimStakeholder outInsure = pushClaimContext.getOutInsure();
        ClaimStakeholder mainInsure = pushClaimContext.getMainInsure();
        ClaimStakeholder collectInsure = pushClaimContext.getCollectInsure();
        List<LiabilityMapping> dutyConfigs = pushClaimContext.getDutyConfigList();
        String insuranceName = claim.getInsuranceName();
        String branchName = claim.getBranchName();
        boolean isRbsOrTbcClaim = SPECIAL_INSURANCE_NAMES.contains(insuranceName)
                || SPECIAL_BRANCH_NAMES.contains(branchName)
                || (insuranceName != null && insuranceName.contains(SIMPLE_DINGHE));
        VisitDutyStrategy strategy = visitDutyStrategyFactory.getStrategy(
                CollectionUtil.isNotEmpty(dutyConfigs), isRbsOrTbcClaim, RENSHOU.equals(insuranceName));

        VisitDutyStrategy strategyDuty = visitDutyStrategyFactory.getStrategy(
                CollectionUtil.isNotEmpty(dutyConfigs), isRbsOrTbcClaim, RENSHOU.equals(insuranceName) || ZHONGYIN.equals(insuranceName));

        Set<String> dutyIds = strategy.getDutyIds(invoiceList);
        if (CollectionUtil.isNotEmpty(dutyIds)) {
            List<String> strings = adjustmentResult.getResultCode();
            String str = null;
            if (CollectionUtil.isNotEmpty(strings)) {
                str = StringUtils.equals(strings.get(0), "P3") ? "3" : StringUtils.equals(strings.get(0), "D") ? "0" : "1";
            }
            String finalStr = str;
            List<ClaimConclusionDTO> conclusionDTOList = dutyIds.stream().filter(dutyId -> StringUtils.isNotEmpty(dutyId)).map(dutyId -> {
                InvoicePropertyBean invoicePropertyBean = strategy.getInvoicePropertyBean(dutyId, invoiceList);
                ClaimConclusionDTO claimConclusionDTO = new ClaimConclusionDTO();

                claimConclusionDTO.setAdjustmentGuid(UUID.randomUUID().toString());
                claimConclusionDTO.setBatchCode("");
                claimConclusionDTO.setGroupPolicy(claim.getPolicyNo());//1.6不支持拆分保单
                claimConclusionDTO.setRecognizeeCertId(outInsure.getIdentityNo());
                //领款人性别
                if (StringUtils.isNotEmpty(collectInsure.getIdentityNo()) && collectInsure.getIdentityNo().length() >= 18) {
                    claimConclusionDTO.setPersonGender(Integer.parseInt(collectInsure.getIdentityNo().substring(16, 17)) % 2 == 0 ? "2" : "1");
                } else {
                    GenderEnum enumByCode = GenderEnum.getEnumByCode(collectInsure.getGender());
                    claimConclusionDTO.setPersonGender(ObjectUtil.defaultIfNull(enumByCode, t -> t.getInsureCode(), null));
                }
                claimConclusionDTO.setPersonName(outInsure.getName());
                String hospitalDate = InvoiceTool.getMinHospitalDate(invoiceList.stream().filter(t -> StringUtils.isNotBlank(t.getHospitalPeriod())).map(invoice -> invoice.getHospitalPeriod()).collect(Collectors.toList()));
                claimConclusionDTO.setPolicyDate(hospitalDate);
                claimConclusionDTO.setPolicyType(2);
                claimConclusionDTO.setInHospitalDate(hospitalDate);
                hospitalDate = InvoiceTool.getMaxHospitalDate(invoiceList.stream().filter(t -> StringUtils.isNotBlank(t.getHospitalPeriod())).map(invoice -> invoice.getHospitalPeriod()).collect(Collectors.toList()));
                claimConclusionDTO.setOutHospitalDate(hospitalDate);
                LiabilityConfig liabilityConfig = batchDataBean.getLiabilityMap().getOrDefault(invoicePropertyBean.getDutyId(), new LiabilityConfig());
                Coverage coverage = batchDataBean.getCoverageMap().getOrDefault(liabilityConfig.getCoverageId(), new Coverage());
                ClaimInvoice claimInvoice = invoicePropertyBean.getClaimInvoices().get(0);
                strategy.setConclusionFieldValue(claimConclusionDTO, liabilityConfig, coverage, claimInvoice, dutyConfigs);
                claimConclusionDTO.setOutPass(claimInvoice.getDiagnosisCn());
                claimConclusionDTO.setDiseaseCode(claimInvoice.getDiagnosis());
                claimConclusionDTO.setHospitalCode(StringUtils.isBlank(claimInvoice.getHospitalCode()) ? HOSPITAL_CODE_NOT_EXISTS : claimInvoice.getHospitalCode());
                claimConclusionDTO.setPayeeCertId(StringUtils.isBlank(collectInsure.getIdentityNo()) ? outInsure.getIdentityNo() : collectInsure.getIdentityNo());
                claimConclusionDTO.setPayeeName(StringUtils.isBlank(collectInsure.getIdentityNo()) ? outInsure.getName() : collectInsure.getName());
                claimConclusionDTO.setPayeePayWay("5"); //默认
                claimConclusionDTO.setPayeeBank(collectInsure.getBranchCodeCn());
                claimConclusionDTO.setPayeeAccount(collectInsure.getAccountNo());
                claimConclusionDTO.setMobile(collectInsure.getPhone());
                claimConclusionDTO.setClaimCode(claim.getClaimNo());
                claimConclusionDTO.setClaimCodeTb(claim.getInsurerClaimNo());
                claimConclusionDTO.setPolicyCode(claim.getSlipPersonPsc());
                claimConclusionDTO.setClaimConclusion(finalStr);
                claimConclusionDTO.setSubcode("01");
                List<AdjustmentRecord> filteredAdjustmentRecordList = new ArrayList<>();
                List<Long> invoiceIds = invoicePropertyBean.getClaimInvoices().stream().map(ClaimInvoice::getId).collect(Collectors.toList());
                for (AdjustmentRecord adjustmentRecord : adjustmentRecordList) {
                    if (StringUtils.equals(adjustmentRecord.getLiabilityUuid(),invoicePropertyBean.getDutyId()) && CollectionUtil.contains(invoiceIds, adjustmentRecord.getInvoiceId())) {
                        filteredAdjustmentRecordList.add(adjustmentRecord);
                    }
                }
                BigDecimal amt = filteredAdjustmentRecordList.stream().map(AdjustmentRecord::getPayoutAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                claimConclusionDTO.setCompensateAmt(amt);
                claimConclusionDTO.setStatus(1);
                claimConclusionDTO.setMemo("");
                claimConclusionDTO.setDutyName(liabilityConfig.getLiabilityName());
                SourceType sourceType = SourceType.getEnumCode(claim.getSource());
                claimConclusionDTO.setIsOnline(ObjectUtil.defaultIfNull(sourceType, t -> t.getDesc(), null));
                claimConclusionDTO.setPayeeCertType(MAGIC_CODE);
                claimConclusionDTO.setPersonCertType(MAGIC_CODE);

                String dutyCode = "";
                if (StringUtils.isNotEmpty(claimConclusionDTO.getInsuType())) {
                    dutyCode += claimConclusionDTO.getInsuType();
                }

                if (StringUtils.isNotEmpty(claimConclusionDTO.getDuty())) {
                    dutyCode += claimConclusionDTO.getDuty();
                }

                if (StringUtils.isNotEmpty(claimConclusionDTO.getDutySubcode())) {
                    dutyCode += claimConclusionDTO.getDutySubcode();
                }
                claimConclusionDTO.setDutyCode( dutyCode );
                claimConclusionDTO.setClaimStatus( 1 );

                AccountTypeEnum accountTypeEnum = AccountTypeEnum.getByCode(liabilityConfig.getAccountType());
                claimConclusionDTO.setPublicPersonalFlag( ObjectUtil.defaultIfNull(accountTypeEnum, t -> String.valueOf(t.getPushValue()), ""));
                LiabilityTypeEnum liabilityType = liabilityConfig.getLiabilityType();
                claimConclusionDTO.setConclusionType(ObjectUtil.defaultIfNull(liabilityType, t -> t.getPushValue(), null));
                QuotaController quotaController = liabilityConfig.getQuotaController();
                if (Objects.nonNull(quotaController)) {
                    QuotaControlSourceEnum quotaControlSourceEnum = QuotaControlSourceEnum.getByCode(quotaController.getType());
                    claimConclusionDTO.setDeductionType(ObjectUtil.defaultIfNull(quotaControlSourceEnum, t -> t.getPushValue(), null));
                    claimConclusionDTO.setDeductGroupPolicy( quotaController.getPolicyNo());
                }
                String ratioPayoutList = null;
                if (CollectionUtil.isNotEmpty(filteredAdjustmentRecordList)) {
                    ratioPayoutList = filteredAdjustmentRecordList.get(0).getRatioPayoutList();
                    if (StringUtils.isEmpty(ratioPayoutList)) {
                        ratioPayoutList = "{}";
                    }
                    Map<String, BigDecimal> map = JSON.parseObject(ratioPayoutList, new TypeReference<>() {
                    });
                    BigDecimal temp = map.get(AmountNameEnum.合理金额.name());
                    if (Objects.nonNull(temp)) {
                        claimConclusionDTO.setReasonableRatio(temp);
                    }
                    temp = map.get(AmountNameEnum.自付二.name());
                    if (Objects.nonNull(temp)) {
                        claimConclusionDTO.setPartialSelfPaymentRatio(temp);
                    }
                    temp = map.get(AmountNameEnum.总自费.name());
                    if (Objects.nonNull(temp)) {
                        claimConclusionDTO.setSelfPaymentRatio(temp);
                    }
                }
                RestrictOutInsure restrictOutInsure = liabilityConfig.getRestrictOutInsure();
                if (Objects.nonNull(restrictOutInsure) && CollectionUtil.isNotEmpty(restrictOutInsure.getLiabilityFeeType())) {
                    restrictOutInsure.getLiabilityFeeType().forEach(z -> {
                        if (AmountNameEnum.合理金额.name().equals(z.getFeeType())) {
                            claimConclusionDTO.setIsCoverReasonable(z.getOpen() ? 1 : 0);
                        }
                        if (AmountNameEnum.自付二.name().equals(z.getFeeType())) {
                            claimConclusionDTO.setIsCoverPartialSelfPayment(z.getOpen() ? 1 : 0);
                        }
                        if (AmountNameEnum.总自费.name().equals(z.getFeeType())) {
                            claimConclusionDTO.setIsCoverSelfPayment(z.getOpen() ? 1 : 0);
                        }
                    });
                }
                claimConclusionDTO.setResponsibilityPattern( 1 );//默认模式一

                claimConclusionDTO.setRelatedObjectGuid( invoicePropertyBean.getDutyId() );
                claimConclusionDTO.setCorpCode( claim.getSerialNumber() );
                claimConclusionDTO.setPersonCertId( outInsure.getIdentityNo() );
                String calculateDuty = strategyDuty.calculateDuty(liabilityConfig, coverage, claimInvoice, dutyConfigs);
                claimConclusionDTO.setVisitDuty( calculateDuty );
                final BigDecimal[] tempInvoiceAmount = {BigDecimal.ZERO};
                invoicePropertyBean.getClaimInvoices().forEach(z -> {
                    if (BigDecimal.ZERO.compareTo(z.getTotalAmount()) == 0) {
                        tempInvoiceAmount[0] = tempInvoiceAmount[0].add(z.getInvalidAmount());
                    } else {
                        tempInvoiceAmount[0] = tempInvoiceAmount[0].add(z.getTotalAmount());
                    }
                });
                claimConclusionDTO.setApplyAmt( tempInvoiceAmount[0] );
                amt = filteredAdjustmentRecordList.stream().map(AdjustmentRecord::getDeductAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                claimConclusionDTO.setAbtmAmt( amt );
                final BigDecimal[] unReasonableAmount = {BigDecimal.ZERO};
                invoicePropertyBean.getClaimInvoices().forEach(l -> {
                    if (l.getInvalidAmount() != null) {
                        unReasonableAmount[0] = unReasonableAmount[0].add(l.getInvalidAmount());
                    }
                });
                claimConclusionDTO.setChangeAmt( unReasonableAmount[0] );
                claimConclusionDTO.setUnCompensateCause( adjustmentResult.getResultDetail() );
                claimConclusionDTO.setPolicyBeginDate( DateUtil.formatDate(policy.getEffdate()) );
                claimConclusionDTO.setPolicyEndDate( DateUtil.formatDate(policy.getExpdate()) );
                claimConclusionDTO.setCompensateDuty( liabilityConfig.getLiabilityName() );
                BigDecimal firstRatio = JsonValueUtils.getFirstValue(ratioPayoutList);
                claimConclusionDTO.setCompensateRatio( firstRatio );
                claimConclusionDTO.setBillAmt( tempInvoiceAmount[0] );

                //冗余字段
                claimConclusionDTO.getExtraFields().put(CLAIM_CONCLUSION_TREATMENT_TYPE, invoicePropertyBean.getTreatmentType());

                return claimConclusionDTO;
            }).collect(Collectors.toList());
            conclusions.addAll(conclusionDTOList);
        }
    }
}
