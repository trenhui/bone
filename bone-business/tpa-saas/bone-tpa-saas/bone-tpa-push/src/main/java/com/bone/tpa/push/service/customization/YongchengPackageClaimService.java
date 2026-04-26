package com.bone.tpa.push.service.customization;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bone.tpa.api.enums.SourceType;
import com.bone.tpa.facade.vo.OptionSetDTO;
import com.bone.tpa.push.bean.PushClaimContext;
import com.bone.tpa.push.constants.CommonConstant;
import com.bone.tpa.push.dto.*;
import com.bone.tpa.push.enums.ClaimChannelEnum;
import com.bone.tpa.push.enums.EinvoiceTypeEnum;
import com.bone.tpa.push.enums.GenderEnum;
import com.bone.tpa.push.enums.ZfIdentityTypeEnum;
import com.bone.tpa.push.feign.response.SysDictDTO;
import com.bone.tpa.push.service.impl.DefaultPackageClaimService;
import com.bone.tpa.push.util.CommonTool;
import com.bone.tpa.push.util.DateTool;
import com.bone.tpa.push.util.OptionSetUtil;
import com.bone.tpa.sdk.adjustment.model.AdjustmentResult;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimStakeholder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.bone.tpa.push.constants.CommonConstant.*;

/**
 * @Author feihaiming
 * @create 2025/10/20 11:33
 */
@Component("yongchengPackageClaimService")
public class YongchengPackageClaimService extends PackageClaimDecorateService {

    @Autowired
    public YongchengPackageClaimService(DefaultPackageClaimService packageClaimService) {
        super(packageClaimService);
    }

    @Override
    public String getHandlerCode() {
        return CommonConstant.YONGCHENG_MAIN_HANDLER_CODE;
    }

    @Override
    public void checkData(Long claimNo, PushClaimContext pushClaimContext) {

    }

    @Override
    public void customizedPackageClaimData(PushClaimContext pushClaimContext, ClaimDetailPushDTO claimPushInfo) {
        Claim claim = pushClaimContext.getClaim();
        AdjustmentResult adjustmentResult = pushClaimContext.getAdjustmentResult();
        ClaimStakeholder mainInsure = pushClaimContext.getMainInsure();
        ClaimStakeholder outInsure = pushClaimContext.getOutInsure();
        ClaimStakeholder collectInsure = pushClaimContext.getCollectInsure();
        ClaimDTO claimDTO = claimPushInfo.getClaim();
        OptionSetDTO outOptionSet = pushClaimContext.getOutOptionSet();
        OptionSetDTO mainOptionSet = pushClaimContext.getMainOptionSet();
        claimDTO.setBatchCode(claim.getInsurerClaimNo());
        OptionSetUtil.getOptionSetValue(mainOptionSet, "setZbbrzjlx", claimDTO, MAININSURE_IDENTITY_TYPE_MAIN_MAPPING_NAME);
        OptionSetUtil.getOptionSetValue(outOptionSet, "setBbrzjlx", claimDTO, OUTINSURE_IDENTITY_TYPE_MAIN_MAPPING_NAME);
        //setVisitDate永诚要优先取回传账单期间,暂时不做
        //setPolicyDate永诚要优先取回传账单期间,暂时不做
        String collectPayType = EXT_FIELD_PREFIX+YC_COLLECTPAYTYPE;
        String collectPayTypeCode = null;
        if(CollectionUtil.isNotEmpty(collectInsure.getExtraProperties())) {
            collectPayTypeCode = (  String)  collectInsure.getExtraProperties().getOrDefault(collectPayType, "");
        }
        if (StringUtils.isNotBlank(collectPayTypeCode)) {
            // 2(20):线下支付 3:支票 F(70):银企直联
            if (Objects.equals(collectPayTypeCode, 70)) {
                claimDTO.setPayeePayType("F");
            } else if (Objects.equals(collectPayTypeCode, 20)) {
                claimDTO.setPayeePayType("2");
            } else {
                claimDTO.setPayeePayType(collectPayTypeCode);
            }
        }

        //扩展字段
        String agentIdentity = EXT_FIELD_PREFIX+YC_AGENTIDENTITY;
        String agentIdentityCode = null;
        if(CollectionUtil.isNotEmpty(claim.getExtraProperties())) {
            agentIdentityCode = (  String)  claim.getExtraProperties().getOrDefault(agentIdentity, "");
        }
        claimDTO.setAgentType(agentIdentityCode);
        String isAgentProcedures = EXT_FIELD_PREFIX+YC_ISAGENTPROCEDURES;
        String isAgentProceduresCode = null;
        if(CollectionUtil.isNotEmpty(claim.getExtraProperties())) {
            isAgentProceduresCode = (  String)  claim.getExtraProperties().getOrDefault(isAgentProcedures, "");
        }
        claimDTO.setIsFormalItiesComplete(isAgentProceduresCode);
        String isAgentValid = EXT_FIELD_PREFIX+YC_ISAGENTVALID;
        String isAgentValidCode = null;
        if(CollectionUtil.isNotEmpty(claim.getExtraProperties())) {
            isAgentValidCode = (  String)  claim.getExtraProperties().getOrDefault(isAgentValid, "");
        }
        claimDTO.setIsIdentityCheck(isAgentValidCode);
        String insurThirdPartyRelation = EXT_FIELD_PREFIX+YC_INSURTHIRDPARTYRELATION;
        String insurThirdPartyRelationCode = null;
        if(CollectionUtil.isNotEmpty(claim.getExtraProperties())) {
            insurThirdPartyRelationCode = (  String)  claim.getExtraProperties().getOrDefault(insurThirdPartyRelation, "");
        }
        claimDTO.setPayThirdReason(insurThirdPartyRelationCode);
        String payThirdPartyRemark = EXT_FIELD_PREFIX+YC_PAYTHIRDPARTYREMARK;
        String payThirdPartyRemarkCode = null;
        if(CollectionUtil.isNotEmpty(claim.getExtraProperties())) {
            payThirdPartyRemarkCode = (  String)  claim.getExtraProperties().getOrDefault(payThirdPartyRemark, "");
        }
        claimDTO.setReturnReason(payThirdPartyRemarkCode);
        GenderEnum enumByCode = GenderEnum.getEnumByCode(outInsure.getGender());
        claimDTO.setPersonSex(ObjectUtil.defaultIfNull(enumByCode, t -> Integer.valueOf(t.getInsureCode()), null));
        String claimAccidentReason = EXT_FIELD_PREFIX+YC_CLAIMACCIDENTREASON;
        String claimAccidentReasonCode = null;
        if(CollectionUtil.isNotEmpty(claim.getExtraProperties())) {
            claimAccidentReasonCode = (  String)  claim.getExtraProperties().getOrDefault(claimAccidentReason, "");
        }
        claimDTO.setDamageCode(claimAccidentReasonCode);
        String collectrelation = EXT_FIELD_PREFIX+YC_COLLECTRELATION;
        String collectrelationCode = null;
        if(CollectionUtil.isNotEmpty(claim.getExtraProperties())) {
            collectrelationCode = (  String)  claim.getExtraProperties().getOrDefault(collectrelation, "");
        }
        claimDTO.setPayeeType(collectrelationCode);
        //永诚设置理赔结论
        List<String> strings = adjustmentResult.getResultCode();
        if (CollectionUtil.isNotEmpty(strings) && CollectionUtil.isNotEmpty(pushClaimContext.getYcClaimConclusion().getSysDicts())) {
            SysDictDTO dto = pushClaimContext.getYcClaimConclusion().getSysDicts().stream().filter(t -> StringUtils.equals(strings.get(0), t.getDictMemo())).findFirst().orElse(null);
            if (Objects.nonNull(dto)) {
                claimDTO.setCancelType(dto.getDictValue());
            }
        }
        if (strings != null && strings.size() > 1) {
            claimDTO.setCancelReasonCode(strings.get(1).split(":")[1]);
        }
        if (ObjectUtil.equals(claim.getSourceCodeCn(), ClaimChannelEnum.YCHGJ.getName())) {
            String claimAccidentType = EXT_FIELD_PREFIX+YC_CLAIM_ACCIDENTTYPE;
            String claimAccidentTypeCode = null;
            if(CollectionUtil.isNotEmpty(claim.getExtraProperties())) {
                claimAccidentTypeCode = (  String)  claim.getExtraProperties().getOrDefault(claimAccidentType, "");
            }
            claimDTO.setPolicyType("".equals(claimAccidentTypeCode) ? null : Integer.parseInt(claimAccidentTypeCode));//ychgj的出险类型是扩展字段
        }
    }

    @Override
    public void customizedPackageClaimImageData(PushClaimContext pushClaimContext, ClaimDetailPushDTO claimPushInfo) {
        List<ClaimImageDTO> claimImageDTOS = claimPushInfo.getImages();
        Claim claim = pushClaimContext.getClaim();
        List<SysDictDTO> sysDicts = pushClaimContext.getSysDicts();
        if (CollectionUtil.isNotEmpty(claimImageDTOS)) {
            claimImageDTOS.forEach(claimImageDTO -> {
                if (StringUtils.isNotBlank(claimImageDTO.getType())) {
                    return;
                }

                String imgType = claimImageDTO.getExtraFields().get(claimImageDTO.getAddress());
                if (StringUtils.isBlank(imgType)) {
                    throw new TpaBizException("图片类型不存在");
                }
                if (ObjectUtil.notEqual(claim.getSourceCodeCn(), ClaimChannelEnum.YCHGJ.getName())) {
                    claimImageDTO.setType(imgType);
                    return;
                }
                // 判断是否特殊标记
                if (!MAGIC_CODE_YCHGJ.equals(claimImageDTO.getName())) {
                    claimImageDTO.setType(imgType);
                    return;
                }
                List<SysDictDTO> filterDicts = sysDicts.stream().filter(k -> k.getDicItemtype().equals(imgType)).collect(Collectors.toList());
                if (CollectionUtil.isEmpty(filterDicts)) {
                    throw new TpaBizException("特殊标记的图片类型不存在");
                } else {
                    claimImageDTO.setType(imgType);
                }
            });
        }
    }

    @Override
    public void customizedPackageClaimDetailData(PushClaimContext pushClaimContext, ClaimDetailPushDTO claimPushInfo) {
        List<ClaimDetailDTO> claimDetailDTOS = claimPushInfo.getDetails();
        Claim claim = pushClaimContext.getClaim();
        ClaimDTO claimDTO = claimPushInfo.getClaim();
        Map<String, Boolean> matchResult = pushClaimContext.getMatchResult();
        if (CollectionUtil.isNotEmpty(claimDetailDTOS)) {
            claimDetailDTOS.forEach(claimDetailDTO -> {
                claimDetailDTO.setBatchCode(claimDTO.getBatchCode());
                if (matchResult.getOrDefault(claimDetailDTO.getHospitalName(), false)) {
                    if (StringUtils.contains(claim.getBranchName(), "北京")) {
                        //医院代码
                        claimDetailDTO.setHospitalCode("BIABIIHP02");
                        //医院名称
                        claimDetailDTO.setHospitalName("非医保医院");
                    } else if (StringUtils.contains(claim.getBranchName(), "上海")) {
                        //医院代码
                        claimDetailDTO.setHospitalCode("999999999");
                        //医院名称
                        claimDetailDTO.setHospitalName("其他");
                    } else {
                        //医院代码
                        claimDetailDTO.setHospitalCode("9999999");
                        //医院名称
                        claimDetailDTO.setHospitalName("本代码表中不存在的其他医院");
                    }
                }

                //dto冗余字段,暂时不做
                //如果pushenddate不为空则取之
                //claimDetailDTO.setOutDate(DateTool.getLastDateStr(invoice.getHospitalPeriod()));
                //如果pushstartdate不为空则取之
                //claimDetailDTO.setVisitDate(claimDTO.getVisitDate());
                List<ClaimDetailExtendDTO> claimDetailExtends = claimDetailDTO.getClaimDetailExtends();
                if (CollectionUtil.isNotEmpty(claimDetailExtends)) {
                    claimDetailExtends.forEach(claimDetailExtend -> {
                        Integer tpaValue = EinvoiceTypeEnum.getTpaValue(claimDetailExtend.getPaperType());
                        if (Objects.nonNull(tpaValue)) {
                            claimDetailExtend.setIsElectronicInvoice(tpaValue);
                        }
                    });
                }
            });
        }
    }

    @Override
    public void customizedPackageClaimConclusionData(PushClaimContext pushClaimContext, ClaimDetailPushDTO claimPushInfo) {
        ClaimDTO claimDTO = claimPushInfo.getClaim();
        List<ClaimConclusionDTO> conclusions = claimPushInfo.getConclusions();
        if (CollectionUtil.isNotEmpty(conclusions)) {
            conclusions.forEach(conclusion -> {
                conclusion.setBatchCode(claimDTO.getBatchCode());
                //dto冗余字段,暂时不做
                //永诚要优先取回传账单期间
                //String startDate1 = getLiveStartDateNew(invoicesList);
                //claim_conclusion.setConclusionInHospitalDate(startDate1);
                //String endDate1 = getLiveEndDateNew(invoicesList);
                //claim_conclusion.setConclusionOutHospitalDate(endDate1);
                //如果pushstartdate不为空则取之
                //String startDate2 = getLiveStartDateNew(invoicesList);
                //claim_conclusion.setConclusionPolicyDate(startDate2);
            });
        }
    }
}
