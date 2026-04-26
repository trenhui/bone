package com.bone.tpa.intelligent.adjustment.service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bone.core.result.PageResult;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.core.util.DateParserUtil;
import com.bone.tpa.api.ApiResult;
import com.bone.tpa.facade.feign.DirectPaymentFeignClient;
import com.bone.tpa.facade.request.QueryBalanceRequest;
import com.bone.tpa.facade.vo.*;
import com.bone.tpa.intelligent.adjustment.converter.PolicyConvert;
import com.bone.tpa.intelligent.adjustment.dto.PolicyDTO;
import com.bone.tpa.intelligent.adjustment.dto.request.PolicyQueryRequest;
import com.bone.tpa.intelligent.adjustment.model.PolicyInfoModel;
import com.bone.tpa.sdk.adjustment.api.AdjustmentEngine;
import com.bone.tpa.sdk.adjustment.enums.PolicyConfigStatusEnum;
import com.bone.tpa.sdk.adjustment.enums.ZFCertTypeEnum;
import com.bone.tpa.sdk.adjustment.model.Liability;
import com.bone.tpa.sdk.adjustment.model.Plan;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.enums.ClaimStageEnum;
import com.bone.tpa.sdk.claim.enums.PersonTypeEnum;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimStakeholder;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.sdk.dao.ClaimStakeholderRepository;
import com.bone.tpa.sdk.dao.impl.PolicyRepository;
import com.bone.tpa.sdk.adjustment.enums.PolicyStatus;
import com.bone.tpa.sdk.adjustment.enums.PolicyType;
import com.bone.tpa.sdk.adjustment.model.Policy;
import com.bone.tpa.sdk.service.ClaimInfoService;
import com.bone.tpa.sdk.service.PolicyBasicService;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PolicyServiceImpl implements PolicyService {
    @Autowired
    private PolicyRepository policyMapper;

    @Autowired
    private PolicyConvert convert;

    @Autowired
    private DirectPaymentFeignClient directPaymentFeignClient;

    @Autowired
    private AdjustmentEngine adjustmentEngine;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ClaimInfoService claimInfoService;

    @Autowired
    private ClaimStakeholderRepository claimStakeholderRepository;

    @Autowired
    private PolicyBasicService policyBasicService;

    @Autowired
    private LiabilityInfoService liabilityInfoService;

    @Override
    public void updatePolicyConfigStatus(String policyNo) {
        Criteria<Policy> criteria = new Criteria<>();
        criteria.eq(Policy::getPolicyNo, policyNo);

        PageResult<Policy> dbList =  policyMapper.pageByCriteria(criteria);

        if (dbList == null || dbList.getData().isEmpty()) {
            throw new TpaBizException(BizErrorCode.NO_RECORD, "保单号" + policyNo + "不存在");
        }

        Policy policy = dbList.getData().get(0);

        policy.setConfigStatus(PolicyConfigStatusEnum.getByCode(policy.getConfigStatus()).getNextStatus());

        policyMapper.update(policy);
    }

    /**
     * 分页查询
     *
     * @param pageQuery
     * @return
     */
    @Override
    public PageResult<PolicyDTO> queryPageByCondition(PolicyQueryRequest pageQuery) {
        Criteria<Policy> criteria = new Criteria();
        if(pageQuery.getPolicyNo()!= null){
            criteria.like(Policy::getPolicyNo,"%"+pageQuery.getPolicyNo()+"%");
        }
        if(pageQuery.getStatus()!= null){
            criteria.eq(Policy::getStatus, pageQuery.getStatus() );
        }
//        if(pageQuery.getType()!= null){
//            criteria.eq(Policy::getType, pageQuery.getType() );
//        }
        if(pageQuery.getExternalPolicyNo()!= null){
            criteria.eq(Policy::getExternalPolicyNo,"%" +pageQuery.getExternalPolicyNo()+"%" );
        }

        criteria.addSort(Criteria.getDefaultIdSort());
        criteria.page(pageQuery.getPageSize(),pageQuery.getPageOffset());
        PageResult<Policy> dbList =  policyMapper.pageByCriteria(criteria);
        PageResult<PolicyDTO> dtoPageResult =   convert(dbList);
        return dtoPageResult;
    }

    private PageResult<PolicyDTO> convert(PageResult<Policy> inList){
        PageResult<PolicyDTO> rs =   convert.toDtoPageResult(inList);
        rs.setData(convert(inList.getData()));
        return rs;
    }

    private List<PolicyDTO> convert(List<Policy> inList){
        List<PolicyDTO>  dtoList =  convert.toDtoList(inList);
        return dtoList;
    }


    /**
     * 获取直付保单信息
     *
     * @param personName      人员姓名
     * @param personCertId    人员身份证号
     * @return
     */
    @Override
    public List<PolicyInfoModel> getPolicyData(String personName, String personCertId, String claimNo) {
        try {

            Map<String,String> submenuNumberMap = new HashMap<>();

            log.info("赔案号:{}新保单信息接口queryPersonInfoList调用", claimNo);
            ApiResult<List<QueryPersonInfoV1>> queryPersonInfoList = directPaymentFeignClient
                    .queryPersonInfoList(personName, personCertId,null);

            log.info("新保单信息接口queryPersonInfoList返回数据:{}",JSON.toJSONString(queryPersonInfoList));
            if (queryPersonInfoList.getCode()!=0) {
                throw new TpaBizException(BizErrorCode.OUTER_CLIENT_ERROR);
            }
//            peopleInfoResponse.setErrorCode(queryPersonInfoList.getCode());


            List<PeopleInfo> peopleInfoList = new ArrayList<>();

            for(QueryPersonInfoV1 queryPersonInfoV1 : queryPersonInfoList.getData()){
                PeopleInfo peopleInfo = new PeopleInfo();
                BeanUtils.copyProperties(queryPersonInfoV1,peopleInfo);

                peopleInfo.setGender(queryPersonInfoV1.getGender());
                peopleInfo.setBankName(queryPersonInfoV1.getBankAccountName());
                peopleInfo.setMedicalInsuranceType(queryPersonInfoV1.getMedicalInsuranceType());
                peopleInfo.setMobile(queryPersonInfoV1.getMobile());
                peopleInfo.setIsSocialSecurity(queryPersonInfoV1.getIsSocialSecurity());

                peopleInfo.setRelationEndTime(DateUtil.formatDateTime(queryPersonInfoV1.getPlanEndTime()));
                peopleInfo.setRelationStartTime(DateUtil.formatDateTime(queryPersonInfoV1.getPlanStartTime()));
                peopleInfo.setRelativeCondition(queryPersonInfoV1.getHierarchy());
                peopleInfo.setRelativePersonPsc(queryPersonInfoV1.getPersonPsc());
                peopleInfo.setAccountExpirationDate(DateUtil.formatDateTime(queryPersonInfoV1.getPlanEndTime()));
                peopleInfo.setAccountEffectiveDate(DateUtil.formatDateTime(queryPersonInfoV1.getPlanStartTime()));
                peopleInfo.setUnderwritingConditions(queryPersonInfoV1.getHierarchy());
                peopleInfo.setPersonPsc(queryPersonInfoV1.getPersonPsc());

                List<PeopleInfoPlan> tpaUnderwritingPlans = new ArrayList<>();
                peopleInfo.setTpaUnderwritingPlans(tpaUnderwritingPlans);
                if(queryPersonInfoV1.getRelationType().equals(20)){
                    PeopleInfoPlan peopleInfoPlan = new PeopleInfoPlan();

                    peopleInfoPlan.setRelativeCondition(queryPersonInfoV1.getHierarchy());
                    peopleInfoPlan.setRelationEndTime(DateUtil.formatDateTime(queryPersonInfoV1.getPlanEndTime()));
                    peopleInfoPlan.setRelationStartTime(DateUtil.formatDateTime(queryPersonInfoV1.getPlanStartTime()));
                    peopleInfoPlan.setRelativePersonPsc(queryPersonInfoV1.getPersonPsc());
                    peopleInfoPlan.setSubmenuNumber(queryPersonInfoV1.getSubmenuNumber());
                    peopleInfoPlan.setHierarchy(queryPersonInfoV1.getHierarchy());
                    peopleInfoPlan.setEndTime(DateUtil.formatDateTime(queryPersonInfoV1.getPlanEndTime()));
                    tpaUnderwritingPlans.add(peopleInfoPlan);
                }
                submenuNumberMap.put(queryPersonInfoV1.getSlipCode() + queryPersonInfoV1.getHierarchy()
                                +queryPersonInfoV1.getPersonCertId()+queryPersonInfoV1.getMainPersonCertId()+peopleInfo.getAccountEffectiveDate(),
                        queryPersonInfoV1.getSubmenuNumber());


                //获取直付额度
                QueryBalanceRequest getPeopleInfoRequest = new QueryBalanceRequest();
                getPeopleInfoRequest.setPersonCertId(queryPersonInfoV1.getMainPersonCertId());
                getPeopleInfoRequest.setPersonName(queryPersonInfoV1.getMainPersonName());
                getPeopleInfoRequest.setSlipCode(queryPersonInfoV1.getSlipCode());

//                        "Request:"+ JSON.toJSONString(getPeopleInfoRequest), "直付额度查询入参");
                log.info("直付额度查询入参:{}",JSON.toJSONString(getPeopleInfoRequest));
                ApiResult<List<QueryBalanceResponse>> balanceResponse
                        = directPaymentFeignClient.queryAccount(getPeopleInfoRequest);
                log.info("直付额度查询反参:{}",JSON.toJSONString(balanceResponse));
//                        "Request:"+ JSON.toJSONString(balanceResponse), "直付额度查询反参");

                if(balanceResponse.getCode()==0){
                    //peopleInfo.setAccountEffectiveDate(queryBalanceResponse.getAccountBegindate());
                    //peopleInfo.setAccountExpirationDate(queryBalanceResponse.getAccountEnddate());
                    peopleInfo.setBankAccount(balanceResponse.getData().get(0).getPersonBankAccount());
                    peopleInfo.setBankAccountName(balanceResponse.getData().get(0).getPersonBankName());
                }

                peopleInfoList.add(peopleInfo);
            }


            List<PolicyInfoModel> policyInfoModelList = new ArrayList<>();

            // 保单号
            List<String> slipCodes = peopleInfoList.stream().map(PeopleInfo::getSlipCode).collect(Collectors.toList());
            List<Policy> policyList = policyBasicService.getPolicyByPolicyNo(slipCodes);

            for (PeopleInfo peopleInfo : peopleInfoList) {


                List<Policy> npPolicyList = policyList.stream().filter(x -> Objects.equals(x.getPolicyNo(), peopleInfo.getSlipCode())).collect(Collectors.toList());
                // 查询是否有特殊提示
//            boolean isSpecialTip = policySpecialDetailMapper
//                    .selectCount(new QueryWrapper<PolicySpecialDetail>()
//                            .eq("policy_code", peopleInfo.slipCode)
//                            .eq("certid", request.getPersonCertId())
//                            .eq("IsDelete", 0)
//                            .isNotNull("remark")) > 0;
                boolean jsRelationFlag = peopleInfo.getRelationType() == 20;
                if (peopleInfo.getTpaUnderwritingPlans() == null || peopleInfo.getTpaUnderwritingPlans().isEmpty()) {
                    PolicyInfoModel policyInfo = new PolicyInfoModel();
                    policyInfo.setPolicyNo(peopleInfo.getSlipCode());
                    if (jsRelationFlag) {
                        //bugfix:直付保单查询接口返回的数据，有些是空的，tpa直接使用，报空指针异常
                        if (peopleInfo.getRelationEndTime() == null || peopleInfo.getRelationStartTime() == null) {
                            throw new TpaBizException(BizErrorCode.OUTER_CLIENT_ERROR, "直付返回的保单分单号开始时间和分单号结束时间为空");
                        }
                        policyInfo.setEndDate(peopleInfo.getRelationEndTime().contains(".") ? peopleInfo.getRelationEndTime().substring(0, peopleInfo.getRelationEndTime().indexOf(".")) : peopleInfo.getRelationEndTime());
                        policyInfo.setStartDate(peopleInfo.getRelationStartTime().contains(".") ? peopleInfo.getRelationStartTime().substring(0, peopleInfo.getRelationStartTime().indexOf(".")) : peopleInfo.getRelationStartTime());
                        policyInfo.setPlanName(peopleInfo.getRelativeCondition());
                        policyInfo.setSlipCode(peopleInfo.getRelativePersonPsc());
                    } else {
                        policyInfo.setEndDate(peopleInfo.getAccountExpirationDate());
                        policyInfo.setStartDate(peopleInfo.getAccountEffectiveDate());
                        policyInfo.setPlanName(peopleInfo.getUnderwritingConditions());
                        policyInfo.setSlipCode(peopleInfo.getPersonPsc());
                    }
                    policyInfo.setRelation(jsRelationFlag
                            ? peopleInfo.getRelation() + "（家属）" : peopleInfo.getRelation());
                    policyInfo.setRelationType(peopleInfo.getRelationType());
                    policyInfo.setMainPersonName(peopleInfo.mainPersonName);
                    policyInfo.setMainPersonCertId(peopleInfo.getMainPersonCertId());
                    policyInfo.setMainPersonCertType(String.valueOf(peopleInfo.getMainPersonCertType()));
                    if(!StringUtils.isEmpty(peopleInfo.getInsuredTime())) {
                        policyInfo.setInsuredTime(DateUtil.parse(peopleInfo.getInsuredTime()));
                    }
                    if(!StringUtils.isEmpty(peopleInfo.getCancellationTime())) {
                        policyInfo.setCancellationTime(DateUtil.parse(peopleInfo.getCancellationTime()));
                    }
                    if(!StringUtils.isEmpty(peopleInfo.getEffectiveTime())) {
                        policyInfo.setEffectiveTime(DateUtil.parse(peopleInfo.getEffectiveTime()));
                    }
                    policyInfo.setMasterRelation(peopleInfo.getMasterRelation());
                    policyInfo.setSecondaryRelation(peopleInfo.getSecondaryRelation());
                    if (!npPolicyList.isEmpty()) {
                        policyInfo.setInsureName(npPolicyList.get(0).getInsureName());
                        policyInfo.setInsuranceName(npPolicyList.get(0).getInsuranceName());
                    }
                    policyInfo.setIsSpecialTip(false);
                    policyInfo.setBankAccount(peopleInfo.getBankAccount());
                    policyInfo.setBankName(peopleInfo.getBankAccountName());
                    policyInfo.setMobile(peopleInfo.getMobile());
                    policyInfo.setCertType(ZFCertTypeEnum.getNameForZF(peopleInfo.getPersonCertType()));
                    policyInfo.setIsCertType(0);

                    if(submenuNumberMap.get(policyInfo.getPolicyNo() + policyInfo.getPlanName() +
                            peopleInfo.getPersonCertId()+peopleInfo.getMainPersonCertId() +peopleInfo.getAccountEffectiveDate() ) != null){
                        policyInfo.setSerialNumber(submenuNumberMap.get(policyInfo.getPolicyNo() + policyInfo.getPlanName() + peopleInfo.getPersonCertId() + peopleInfo.getMainPersonCertId()
                                + peopleInfo.getAccountEffectiveDate()));
                    }
                    policyInfo.setInCustomerNo(peopleInfo.getInCustomerNo());
                    policyInfoModelList.add(policyInfo);
                } else {
                    for (PeopleInfoPlan underwritingPlan : peopleInfo.getTpaUnderwritingPlans()) {
                        PolicyInfoModel policyInfo = new PolicyInfoModel();

                        policyInfo.setPolicyNo(peopleInfo.getSlipCode());
                        if (jsRelationFlag) {
                            policyInfo.setRelation(peopleInfo.getRelation() + "（家属）");
                            policyInfo.setPlanName(underwritingPlan.getRelativeCondition());

                            if(!StringUtils.isEmpty(underwritingPlan.getEndTime())) {

                                //TPADD-297
                                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                try {
                                    Date date1 = format.parse("2023-01-01 00:00:00");
                                    Date date2 = format.parse(underwritingPlan.getEndTime());
                                    if (date2.compareTo(date1) < 0) {
                                        if (peopleInfo.getRelationEndTime() == null || peopleInfo.getRelationStartTime() == null) {
                                            policyInfo.setEndDate("");
                                            policyInfo.setStartDate("");
                                        }else{
                                            policyInfo.setEndDate(underwritingPlan.getRelationEndTime().contains(".") ? underwritingPlan.getRelationEndTime().substring(0, underwritingPlan.getRelationEndTime().indexOf(".")) : underwritingPlan.getRelationEndTime());
                                            policyInfo.setStartDate(underwritingPlan.getRelationStartTime().contains(".") ? underwritingPlan.getRelationStartTime().substring(0, underwritingPlan.getRelationStartTime().indexOf(".")) : underwritingPlan.getRelationStartTime());
                                        }
                                    }else{
                                        policyInfo.setEndDate(underwritingPlan.getRelationEndTime().contains(".") ? underwritingPlan.getRelationEndTime().substring(0, underwritingPlan.getRelationEndTime().indexOf(".")) : underwritingPlan.getRelationEndTime());
                                        policyInfo.setStartDate(underwritingPlan.getRelationStartTime().contains(".") ? underwritingPlan.getRelationStartTime().substring(0, underwritingPlan.getRelationStartTime().indexOf(".")) : underwritingPlan.getRelationStartTime());
                                    }
                                } catch (Exception ex) {

                                }
                            }


                            //policyInfo.endDate = underwritingPlan.getRelationEndTime().contains(".") ? underwritingPlan.getRelationEndTime().substring(0, underwritingPlan.getRelationEndTime().indexOf(".")) : underwritingPlan.getRelationEndTime();
                            //policyInfo.startDate = underwritingPlan.getRelationStartTime().contains(".") ? underwritingPlan.getRelationStartTime().substring(0, underwritingPlan.getRelationStartTime().indexOf(".")) : underwritingPlan.getRelationStartTime();
                            policyInfo.setSlipCode(underwritingPlan.getRelativePersonPsc());
                        } else {
                            policyInfo.setRelation(peopleInfo.getRelation());
                            policyInfo.setPlanName(underwritingPlan.getHierarchy());
                            policyInfo.setEndDate(underwritingPlan.getEndTime());
                            policyInfo.setStartDate(underwritingPlan.getStartTime());
                            policyInfo.setSlipCode(peopleInfo.getPersonPsc());
                        }
                        policyInfo.setRelationType(peopleInfo.getRelationType());
                        policyInfo.setSerialNumber(underwritingPlan.getSubmenuNumber());

                        policyInfo.setMainPersonName(peopleInfo.mainPersonName);
                        policyInfo.setMainPersonCertId(peopleInfo.getMainPersonCertId());
                        policyInfo.setMainPersonCertType(String.valueOf(peopleInfo.getMainPersonCertType()));
                        policyInfo.setInsuredTime(DateUtil.parse(peopleInfo.getInsuredTime()));
                        policyInfo.setCancellationTime(DateUtil.parse(peopleInfo.getCancellationTime()));
                        policyInfo.setEffectiveTime(DateUtil.parse(peopleInfo.getEffectiveTime()));
                        policyInfo.setMasterRelation(peopleInfo.getMasterRelation());
                        policyInfo.setSecondaryRelation(peopleInfo.getSecondaryRelation());
                        if (!npPolicyList.isEmpty()) {
                            policyInfo.setInsureName(npPolicyList.get(0).getInsureName());
                            policyInfo.setInsuranceName(npPolicyList.get(0).getInsuranceName());
                        }
                        policyInfo.setIsSpecialTip(false);
                        policyInfo.setBankAccount(peopleInfo.getBankAccount());
                        policyInfo.setBankName(peopleInfo.getBankAccountName());
                        policyInfo.setMobile(peopleInfo.getMobile());
                        policyInfo.setCertType(ZFCertTypeEnum.getNameForZF(peopleInfo.getPersonCertType()));
                        policyInfo.setIsCertType(0);

                        policyInfo.setInCustomerNo(peopleInfo.getInCustomerNo());
                        policyInfoModelList.add(policyInfo);
                    }
                }
            }



            if (CollectionUtil.isNotEmpty(policyInfoModelList)) {
                List<PolicyInfoModel> infoModels = new ArrayList<>(policyInfoModelList);
                // 保单号
                List<String> policyNos = infoModels.stream().map(PolicyInfoModel::getPolicyNo).collect(Collectors.toList());

                List<Policy> parentPolicies = policyBasicService.getPolicyByParentPolicyNo(policyNos);

                infoModels.forEach(p -> {
                    LocalDateTime peopleStart = DateUtil.parseLocalDateTime(p.getStartDate(), "yyyy-MM-dd HH:mm:ss");
                    LocalDateTime peopleEnd = DateUtil.parseLocalDateTime(p.getEndDate(), "yyyy-MM-dd HH:mm:ss");
                    List<Policy> parentPolicyList = parentPolicies.stream().filter(x -> Objects.equals(x.getParentPolicyNo(), p.getPolicyNo())).collect(Collectors.toList());
                    if (CollectionUtil.isNotEmpty(parentPolicyList)) {
                        parentPolicyList.forEach(f -> {
                            boolean flag = false;
                            LocalDateTime policyStart = LocalDateTimeUtil.of(f.getEffdate());
                            LocalDateTime policyEnd = LocalDateTimeUtil.of(f.getExpdate());
                            PolicyInfoModel infoModel = BeanUtil.copyProperties(p, PolicyInfoModel.class);
                            infoModel.setPolicyNo(f.getPolicyNo());
                            infoModel.setInsureName(f.getInsureName());
                            infoModel.setInsuranceName(f.getInsuranceName());
                            if (LocalDateTimeUtil.isIn(peopleStart, policyStart, policyEnd, true, true)
                                    && (peopleEnd.isEqual(policyEnd) || peopleEnd.isAfter(policyEnd))) {
                                infoModel.setEndDate(DateUtil.format(f.getExpdate(), DatePattern.NORM_DATETIME_PATTERN));
                                flag = true;

                            } else if ((peopleStart.isEqual(policyStart) || peopleStart.isBefore(policyStart))
                                    && LocalDateTimeUtil.isIn(peopleEnd, policyStart, policyEnd, true, true)) {
                                infoModel.setStartDate(DateUtil.format(f.getEffdate(), DatePattern.NORM_DATETIME_PATTERN));
                                flag = true;
                            } else if (peopleStart.isBefore(policyStart) && peopleEnd.isAfter(policyEnd)) {
                                infoModel.setStartDate(DateUtil.format(f.getEffdate(), DatePattern.NORM_DATETIME_PATTERN));
                                infoModel.setEndDate(DateUtil.format(f.getExpdate(), DatePattern.NORM_DATETIME_PATTERN));
                                flag = true;
                            } else if (LocalDateTimeUtil.isIn(peopleStart, policyStart, policyEnd, true, true)
                                    && LocalDateTimeUtil.isIn(peopleEnd, policyStart, policyEnd, true, true)) {
                                flag = true;

                            }
                            if (flag) {
                                policyInfoModelList.add(infoModel);
                            }
                        });
                    }
                });
                Set<PolicyInfoModel> infoModelSet = new HashSet<>(infoModels);
                infoModels.clear();
                infoModels.addAll(infoModelSet);
            }

            int count = 1;
            for (PolicyInfoModel policyInfo : policyInfoModelList) {
                if (policyInfo.getSerialNumber() == null || policyInfo.getSerialNumber().isEmpty()) {
                    policyInfo.setSerialNumber(Integer.toString(count));
                    count += 1;
                }
            }


            return policyInfoModelList;
        } catch (Exception ex) {
            log.error("获取保单信息异常", ex);

            return new ArrayList<>();
        }
    }

    @Override
    public List<PolicyInfoModel> getPolicyDataForReviewing(String claimNo) {



        return List.of();
    }


    public Boolean savePolicyInfo(PolicyInfoModel policyInfoModel, Long claimId) {
        try {
            if (claimId == null) {
                return false;
            }

            Claim claim = claimRepository.findById(claimId);

            if (!claim.getStage().equals(ClaimStageEnum.AUDITING.getCode())) {
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "审核阶段赔案才能修改保单号!");
            }

            //取出被保险人
            ClaimStakeholder mainInsure;
            List<ClaimStakeholder> stakeholderList = claimInfoService.getStakeHolder(claim.getId(), claim.getBizIdentityCode(), claim.getTenantId(), PersonTypeEnum.MAIN_INSURE.getCode(), false);
            if (stakeholderList == null) {
                mainInsure = new ClaimStakeholder();
                mainInsure.setRelatedId(claim.getId());
                mainInsure.setPersonType(PersonTypeEnum.MAIN_INSURE.getCode());
            } else {
                mainInsure = stakeholderList.get(0);
            }

            // 查询保单号对应的保险公司和投保公司
            Policy policy = policyBasicService.getPolicyByPolicyNo(policyInfoModel.getPolicyNo());

            // 这个报案相关的我们先不管
//            String caseNo = claim.getInsurerClaimNo();
//            if (caseNo != null && !caseNo.isEmpty()) {
//                QueryWrapper<tpc_claim> tpcClaimQueryWrapper = new QueryWrapper<>();
//                tpcClaimQueryWrapper.eq("is_deleted", 0).eq("claim_code", caseNo);
//                List<tpc_claim> tpcClaims = tpcClaimMapper.selectList(tpcClaimQueryWrapper);
//                if (tpcClaims.size() > 0) {
//                    if (scClaimdetails.get(0).getInsurancename().contains("永诚")) {
//                        if ("ychgj".equals(tpcClaims.get(0).getChannel())) {
//                            //加一个判断
//                            List<sys_dict> dicts = sysDictMapper.selectList(new QueryWrapper<sys_dict>().eq("dict_type", "YcPolicy")
//                                    .eq("dict_deleted", 0).eq("dict_status", 1));
//
//                            List<sys_dict> tempDicts = dicts.stream().filter(r -> r.getDictDisplaytext().equals(tpcClaims.get(0).getSlipCode())).collect(Collectors.toList());
//                            if (tempDicts != null && tempDicts.size() > 0) {
//                            } else {
//                                if (!tpcClaims.get(0).getSlipCode().equals(request.getPolicyNo())) {
//                                    return Result.Failed("请选择正确的保单号!");
//                                }
//                            }
//                            //新增永诚申请人证件号姓名和保单证件号姓名校验
//                            if (tpcClaims.get(0).getMaininsureidentityno() != null && request.getMainInsureIdentityNo() != null
//                                    && !tpcClaims.get(0).getMaininsureidentityno().equals(request.getMainInsureIdentityNo())) {
//                                return Result.Failed("此次申请为主被保单，请核实！");
//                            }
//                            if (tpcClaims.get(0).getMaininsurename() != null && request.getMainInsureName() != null
//                                    && !tpcClaims.get(0).getMaininsurename().equals(request.getMainInsureName())) {
//                                return Result.Failed("此次申请为主被保单，请核实！");
//                            }
//                        }
//                    }
//                }
//            }

            // 清除理算，释放额度
            adjustmentEngine.clearClaimAdjustment(claim.getId());

//            // 维护保单信息 todo 朱子元
//            policy.setEffdate(DateParserUtil.parseDate(policyInfoModel.getStartDate()));
//            policy.setExpdate(DateParserUtil.parseDate(policyInfoModel.getEndDate()));


//            policyMapper.update(policy);

            // 更新赔案

            //保单信息
            claim.setPolicyNo(policyInfoModel.getPolicyNo());
            claim.setPolicyStartDate(policyInfoModel.getStartDate());
            claim.setPolicyEndDate(policyInfoModel.getEndDate());
            claim.setPolicyRelation(policyInfoModel.getRelation());
            claim.setRelationType(Optional.ofNullable(policyInfoModel.getRelationType()).orElse(1));

            if (policyInfoModel.getRelation() != null && !policyInfoModel.getRelation().isBlank() && policyInfoModel.getRelation().contains("（家属）") ) {
                claim.setPolicyRelation(policyInfoModel.getRelation().replace("（家属）", ""));
                claim.setRelationType(20);
            }

            //填入计划信息
            if (policyInfoModel.getPlanName() != null && !policyInfoModel.getPlanName().isBlank()) {
                Plan plan = liabilityInfoService.getPlanByName(policyInfoModel.getPlanName());

                claim.setPlanUuid(plan.getUuid());
            } else {
                claim.setPlanUuid("");
            }

            //维护个单号
            claim.setSlipPersonPsc(policyInfoModel.getSlipCode() == null ? "" : policyInfoModel.getSlipCode());
            claim.setSerialNumber(policyInfoModel.getSerialNumber() == null ? "" : policyInfoModel.getSerialNumber());

            //维护投保公司、保险公司及其分支机构
            claim.setInsureName(policy.getInsureName());
//            claim.setInsure(policy.getInsureId());
            claim.setBranchName(policy.getInsuranceName());
//            claim.setBranchCompanyId(policy.getCarrierId());
            claim.setInsuranceName(policy.getInsuranceName());
//            claim.setInsuranceCompanyId(policy.getPolicyNo());

            claimRepository.update(claim);

            //更新被保险人
            mainInsure.setRelationToOutInsureCn(policyInfoModel.getRelation());
            mainInsure.setName(policyInfoModel.getMainPersonName());
            mainInsure.setIdentityType(policyInfoModel.getMainPersonCertType());
            mainInsure.setIdentityTypeCn(ZFCertTypeEnum.getNameForZF(Integer.parseInt(policyInfoModel.getMainPersonCertType())));
            mainInsure.setIdentityNo(policyInfoModel.getMainPersonCertId());

            claimStakeholderRepository.save(mainInsure);

            //这里是特约信息
//            QueryWrapper<PolicySpecialDetail> policySpecialDetailQueryWrapper = new QueryWrapper<>();
//
//            List<sc_claimdetailxp> claimdetails = claimdetailxpMapper.selectList(new QueryWrapper<sc_claimdetailxp>().eq("ClaimNumber", request.claimNo));
//            //获取主客户号
//            getPeopleInfo(scClaimdetails.get(0),claimdetails.get(0));
//            policySpecialDetailQueryWrapper.eq("policy_code", request.policyNo).eq("certid", claimdetails.get(0).getOutinsureidentityno()).eq("name", claimdetails.get(0).getOutinsurename());
//            policySpecialDetailQueryWrapper.eq("IsDelete", 0);
//            List<PolicySpecialDetail> specialDetails = policySpecialDetailMapper.selectList(policySpecialDetailQueryWrapper);
//
//            String specialRemark = "";
//            if (specialDetails != null && specialDetails.size() > 0) {
//                specialRemark = specialDetails.get(0).getRemark();
//            }
            //todo 这里是自动绑定责任

        } catch (Exception ex) {
            log.error("savePolicyInfo fail", ex);
            throw new RuntimeException(ex);
        }

        return true;
    }
}
