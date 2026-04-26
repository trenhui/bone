package com.bone.tpa.push.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.enums.FieldModelDefine;
import com.bone.tpa.facade.vo.OptionSetDTO;
import com.bone.tpa.push.bean.PushClaimContext;
import com.bone.tpa.push.dto.*;
import com.bone.tpa.push.enums.PushTypeEnum;
import com.bone.tpa.push.feign.request.HospitalNameCheckRequest;
import com.bone.tpa.push.feign.request.QueryReviewFlagRequest;
import com.bone.tpa.push.feign.request.TpaPersonalImageQueryRequest;
import com.bone.tpa.push.feign.response.*;
import com.bone.tpa.push.service.*;
import com.bone.tpa.push.util.CommonTool;
import com.bone.tpa.push.util.ExceptionUtil;
import com.bone.tpa.push.util.InvoiceTool;
import com.bone.tpa.push.util.ServiceUtil;
import com.bone.tpa.sdk.adjustment.exception.DataNotFoundException;
import com.bone.tpa.sdk.adjustment.model.*;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.sdk.claim.enums.InsurancePushStatusEnum;
import com.bone.tpa.sdk.claim.enums.PersonTypeEnum;
import com.bone.tpa.sdk.claim.enums.PkPushStatusEnum;
import com.bone.tpa.sdk.claim.model.*;
import com.bone.tpa.sdk.dao.*;
import com.bone.tpa.sdk.dao.impl.PolicyRepository;
import com.bone.tpa.sdk.service.AdjustmentRecordBasicService;
import com.bone.tpa.sdk.service.ClaimInfoService;
import com.bone.tpa.sdk.service.LiabilityInfoBasicService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.bone.tpa.push.constants.CommonConstant.*;

/**
 * @Author feihaiming
 * @create 2025/10/20 14:19
 */
@Component
@Slf4j
public class PushClaimInnerServiceImpl implements PushClaimInnerService, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Autowired
    @Qualifier("defaultPackageClaimService")
    private AbstractPackageClaimService packageClaimDefaultService;

    @Autowired
    private ClaimInfoService claimInfoService;

    @Autowired
    private AdjustmentRecordBasicService adjustmentRecordBasicService;

    @Autowired
    @Qualifier("pushLiabilityServiceImpl")
    private LiabilityService liabilityService;

    @Autowired
    private ClaimImageService claimImageService;

    @Autowired
    private SignRecordService signRecordService;

    @Autowired
    private InvoiceImageRelationRepository invoiceImageRelationRepository;

    @Autowired
    private InvoiceProjectRepository invoiceProjectRepository;

    @Autowired
    private InvoiceProjectItemRepository invoiceProjectItemRepository;

    @Autowired
    @Qualifier("pushConfigAdapterHandler")
    private ConfigAdapterAbstractHandler configAdapterHandler;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private InsurancebizService insurancebizService;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private PageModelConfigService pageModelConfigService;

    @Autowired
    private LiabilityInfoBasicService liabilityInfoBasicService;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void push(Long claimNo, String pushType) {
        if (StringUtils.isEmpty(pushType)) {
            pushType = PushTypeEnum.DB_PUSH.getCode();
        }
        Claim claim = claimInfoService.getClaim(claimNo);
        ClaimStakeholder collectInsure = claimInfoService.getStakeHolder(claimNo, claim.getBizIdentityCode(), claim.getTenantId(), PersonTypeEnum.COLLECT.getCode(), true).get(0);
        String collectPayType = EXT_FIELD_PREFIX+YC_COLLECTPAYTYPE;
        String collectPayTypeCode = null;
        if(CollectionUtil.isNotEmpty(collectInsure.getExtraProperties())) {
            collectPayTypeCode = (  String)  collectInsure.getExtraProperties().getOrDefault(collectPayType, "");
        }
        if (StringUtils.isNotBlank(collectPayTypeCode) && ObjectUtil.equals(collectPayTypeCode, "2")) {
            Claim curClaim = new Claim();
            curClaim.setId(claim.getId());
            curClaim.setPkPushStatus(PkPushStatusEnum.PUSH_SUCCESS.getName());
            curClaim.setInsurancePushStatus(InsurancePushStatusEnum.PUSH_FAILED.getName());
            curClaim.setPushBackReason("太保踢回,等支持集中转账再推送"); //获取踢回原因
            claimRepository.update(curClaim);
            return;
        }
        List<AdjustmentRecord> adjustmentRecordList = adjustmentRecordBasicService.getAdjustmentRecordForPush(claimNo);
        if (ObjectUtil.isEmpty(adjustmentRecordList)) {
            throw new DataNotFoundException("未找到理算数据" + claimNo);
        }
        List<AdjustmentResult> adjustmentResultList = adjustmentRecordBasicService.getAdjustmentResultByClaim(claimNo, true);
        if (ObjectUtil.isEmpty(adjustmentResultList)) {
            throw new DataNotFoundException("未找到理算数据" + claimNo);
        }
        List<ClaimInvoice> invoiceList = claimInfoService.getInvoiceList(claimNo, claim.getBizIdentityCode(), claim.getTenantId());
        String liabilityUUID = InvoiceTool.getFirstLiabilityUUID(invoiceList.get(0).getRelateLiability());
        if (liabilityUUID == null) {
            throw new DataNotFoundException("未找到关联责任" + invoiceList.get(0).getRelateLiability());
        }
        String planVersion = adjustmentRecordList.get(0).getVersion();
        List<LiabilityConfig> liabilityConfigs = liabilityInfoBasicService.queryLiabilityByUuidAndVersion(Arrays.asList(liabilityUUID), planVersion);
        if (CollectionUtil.isEmpty(liabilityConfigs)) {
            throw new DataNotFoundException("未找到关联责任" + invoiceList.get(0).getRelateLiability());
        }
        // 第1张发票第1个责任
        LiabilityConfig liabilityConfig = liabilityConfigs.get(0);
        Coverage coverage = liabilityService.getCoverage(liabilityConfig.getPlanId(), planVersion);
        if (coverage == null) {
            throw new DataNotFoundException("未找到责任关联的险种" + JSON.toJSONString(liabilityConfig));
        }
        Plan plan = liabilityService.getPlan(liabilityConfig.getPlanId(), planVersion);
        if (plan == null) {
            throw new DataNotFoundException("未找到责任关联的计划" + JSON.toJSONString(liabilityConfig));
        }
        List<ClaimImage> claimImageList = claimImageService.getClaimImageList(claimNo);
        SignRecord signRecord = signRecordService.getSignRecord(claim.getBatchNo());
        if (signRecord == null) {
            throw new DataNotFoundException("未找到签收记录,批次号：" + claim.getBatchNo());
        }
        Criteria<InvoiceImageRelation> criteria = Criteria.create();
        List<InvoiceImageRelation> imageRelations = invoiceImageRelationRepository.findByCriteria(criteria.eq(InvoiceImageRelation::getClaimId, claimNo));
        List<Long> invoiceIds = invoiceList.stream().map(ClaimInvoice::getId).collect(Collectors.toList());
        Criteria<InvoiceProject> projectCriteria = Criteria.create();
        projectCriteria.eq(InvoiceProject::getTenantId, claim.getTenantId());
        projectCriteria.eq(InvoiceProject::getBizIdentityCode, claim.getBizIdentityCode());
        projectCriteria.in(CollectionUtil.isNotEmpty(invoiceIds), InvoiceProject::getRelatedId, invoiceIds);
        List<InvoiceProject> invoiceProjectList = invoiceProjectRepository.findByCriteria(projectCriteria);
        Criteria<InvoiceProjectItem> itemCriteria = Criteria.create();
        itemCriteria.eq(InvoiceProjectItem::getTenantId, claim.getTenantId());
        itemCriteria.eq(InvoiceProjectItem::getBizIdentityCode, claim.getBizIdentityCode());
        itemCriteria.in(CollectionUtil.isNotEmpty(invoiceIds), InvoiceProjectItem::getRelatedId, invoiceIds);
        List<InvoiceProjectItem> invoiceProjectItemList = invoiceProjectItemRepository.findByCriteria(itemCriteria);
        List<LiabilityMapping> dutyConfigs = configAdapterHandler.handleSDutyConfigs(claim.getPolicyNo());
//        if (CollectionUtil.isEmpty(dutyConfigs)) {
//            throw new DataNotFoundException("未找到责任险种代码配置" + claimNo + "," + claim.getPolicyNo());
//        }
        BigDecimal payAmount = adjustmentRecordList.stream().map(AdjustmentRecord::getPayoutAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        String reviewFlag = insurancebizService.queryReviewFlag(new QueryReviewFlagRequest(claim.getPolicyNo(), claim.getClaimNo(), String.valueOf(payAmount.toString())));
        String policyConfig = insurancebizService.queryPolicyConfig(QueryReviewFlagRequest.builder().policyNo(claim.getPolicyNo()).claimCode(claim.getClaimNo()).build());
        Criteria<Policy> criteriaPolicy = Criteria.create();
        criteriaPolicy.eq(Policy::getPolicyNo, claim.getPolicyNo());
        Policy policy = policyRepository.findOneByCriteria(criteriaPolicy);
        if (Objects.isNull(policy)) {
            throw new DataNotFoundException("未找到保单" + claim.getPolicyNo());
        }
        ClaimStakeholder outInsure = claimInfoService.getStakeHolder(claimNo, claim.getBizIdentityCode(), claim.getTenantId(), PersonTypeEnum.OUT_INSURE.getCode(), true).get(0);
        ClaimStakeholder mainInsure = claimInfoService.getStakeHolder(claimNo, claim.getBizIdentityCode(), claim.getTenantId(), PersonTypeEnum.MAIN_INSURE.getCode(), true).get(0);
        List<ClaimStakeholder> beneInsures = claimInfoService.getStakeHolder(claimNo, claim.getBizIdentityCode(), claim.getTenantId(), PersonTypeEnum.BENEFIT.getCode(), false);
        List<PersonalImageResponse> personalClaimImageList = configAdapterHandler.handlePersonalClaimImages(new TpaPersonalImageQueryRequest(outInsure.getIdentityNo(), outInsure.getName(), claim.getInsuranceName()));
        List<String> hospitalNames = invoiceList.stream().filter(t -> StringUtils.isNotBlank(t.getHospitalName())).map(invoice -> invoice.getHospitalName()).distinct().collect(Collectors.toList());
        HospitalNameCheckResponse hospitalNameCheckResponse = configAdapterHandler.handleHospitalExistsCheck(new HospitalNameCheckRequest(hospitalNames));
        SystemDictResponse systemDictResponse = configAdapterHandler.handleSystemDictInfo("ycImageType");
        SystemDictResponse ycClaimConclusion = configAdapterHandler.handleSystemDictInfo("yc_claim_conclusion");
        CompanyInfoResponse companyInfoResponse = configAdapterHandler.handleCompanyInfo(claim.getBranchName());
        OptionSetDTO mainOptionSet = pageModelConfigService.getOptionSet(EXT_FIELD_PREFIX + CERTIFICATETYPE + YONGCHENG, claim.getBizIdentityCode(), FieldModelDefine.主被保险人, mainInsure.getIdentityType());
        OptionSetDTO outOptionSet = pageModelConfigService.getOptionSet(EXT_FIELD_PREFIX + CERTIFICATETYPE + YONGCHENG, claim.getBizIdentityCode(), FieldModelDefine.出险人, outInsure.getIdentityType());
        OptionSetDTO collectOptionSet = pageModelConfigService.getOptionSet(EXT_FIELD_PREFIX + CERTIFICATETYPE + YONGCHENG, claim.getBizIdentityCode(), FieldModelDefine.领款人信息, collectInsure.getIdentityType());
        OptionSetDTO relationToMainInsureOptionSet = pageModelConfigService.getOptionSet(EXT_FIELD_PREFIX + RELATIONSHIP + YONGCHENG, claim.getBizIdentityCode(), FieldModelDefine.领款人信息, collectInsure.getRelationToMainInsure());
        //永诚没有受益人信息，先注释
//        OptionSetDTO relationToOutInsureOptionSet = null;
        ClaimStakeholder beneInsure = null;
        if (CollectionUtil.isNotEmpty(beneInsures)) {
            beneInsure = beneInsures.get(0);
//            relationToOutInsureOptionSet = pageModelConfigService.getOptionSet(EXT_FIELD_PREFIX + RELATIONSHIP + YONGCHENG, claim.getBizIdentityCode(), FieldModelDefine.受益人, beneInsures.get(0).getRelationToOutInsure());
        }

        PushClaimRequestDTO pushClaimRequestDTO = new PushClaimRequestDTO();
        pushClaimRequestDTO.setPushType(pushType);
        pushClaimRequestDTO.setPushTime(System.currentTimeMillis());
        pushClaimRequestDTO.setInsuranceName(claim.getInsuranceName());
        pushClaimRequestDTO.setBranchName(claim.getBranchName());
        pushClaimRequestDTO.setInsureName(claim.getInsureName());
        ClaimDetailPushDTO claimDetailPushInfo = new ClaimDetailPushDTO();
        pushClaimRequestDTO.setClaimPushInfo(claimDetailPushInfo);
        ClaimDTO claimInfo = new ClaimDTO();
        claimDetailPushInfo.setClaim(claimInfo);
        List<ClaimImageDTO> images = new ArrayList<>();
        claimDetailPushInfo.setImages(images);
        List<ClaimDetailDTO> details = new ArrayList<>();
        claimDetailPushInfo.setDetails(details);
        List<ClaimConclusionDTO> conclusions = new ArrayList<>();
        claimDetailPushInfo.setConclusions(conclusions);
        PushClaimContext pushClaimContext = new PushClaimContext(claim, adjustmentRecordList, adjustmentResultList.get(0), invoiceList, coverage, plan, claimImageList, signRecord, outInsure, mainInsure, collectInsure, beneInsure, imageRelations, invoiceProjectList, invoiceProjectItemList, dutyConfigs, policy, reviewFlag, policyConfig, personalClaimImageList, hospitalNameCheckResponse.getMatchResult(), systemDictResponse.getSysDicts(), companyInfoResponse, mainOptionSet, outOptionSet, collectOptionSet, relationToMainInsureOptionSet, ycClaimConclusion);
        AbstractPackageClaimService handler = ServiceUtil.getHandler(applicationContext, claim.getInsuranceName(), claim.getBranchName(), claim.getInsureName(), packageClaimDefaultService);
        handler.packageData(claimNo, pushClaimContext, claimDetailPushInfo);
        PushClaimAction action = ServiceUtil.getAction(applicationContext, pushType);
        if (action == null) {
            throw new RuntimeException("未找到该赔案  claimId：" + claimNo + "对应的处理方法"+pushType);
        }

        try {
            action.push(pushClaimRequestDTO, pushClaimContext);
            action.afterCompletion(pushClaimRequestDTO, pushClaimContext);
        } catch (Exception e) {
            log.error("推送失败，claimNo={}", claimNo, e);
            Claim curClaim = new Claim();
            curClaim.setId(claim.getId());
            curClaim.setPkPushStatus(PkPushStatusEnum.PUSH_SUCCESS.getName());
            curClaim.setInsurancePushStatus(InsurancePushStatusEnum.PUSH_FAILED.getName());
            curClaim.setErrorType("普康");
            curClaim.setPushBackReason(ExceptionUtil.getExceptionString(e));
            claimRepository.update(curClaim);
            throw e;
        }
    }
}
