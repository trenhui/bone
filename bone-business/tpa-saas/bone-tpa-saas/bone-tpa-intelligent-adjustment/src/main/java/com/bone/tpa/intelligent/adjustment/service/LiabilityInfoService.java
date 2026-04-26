package com.bone.tpa.intelligent.adjustment.service;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.sdk.adjustment.enums.LiabilityToBindStatusEnum;
import com.bone.tpa.sdk.adjustment.enums.LiabilityTypeEnum;
import com.bone.tpa.sdk.claim.enums.VisitTypeEnum;
import com.bone.tpa.sdk.service.LiabilityConverter;
import com.bone.tpa.intelligent.adjustment.model.LiabilityToBind;
import com.bone.tpa.sdk.adjustment.exception.DataNotFoundException;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.sdk.adjustment.enums.PlanStatus;
import com.bone.tpa.sdk.adjustment.model.*;
import com.bone.tpa.sdk.dao.impl.*;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 责任信息准备服务
 */

@Service
@Slf4j
public class LiabilityInfoService {
    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private CoverageRepository coverageRepository;

    @Autowired
    private LiabilityRepository liabilityRepository;

    @Autowired
    private LiabilityConverter liabilityConverter;

    @Autowired
    private LiabilitySharingRepository liabilitySharingRepository;

    @Autowired
    private LiabilitySharingRelationRepository liabilitySharingRelationRepository;

    @Autowired
    private LiabilityService liabilityService;


    public Plan getplan(Long planId) {
        Plan plan = planRepository.findById(planId);
        if (plan == null) {
            throw new DataNotFoundException("未找到该计划  planId：" + planId);
        }

        return plan;
    }

    public Plan getPlanByName(String planName) {
        if (planName == null || planName.isBlank()) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "计划名称为空");
        }

        Criteria<Plan> criteria = Criteria.create();
        criteria.eq(Plan::getPlanName, planName);
        criteria.eq(Plan::getStatus, PlanStatus.ACTIVE.getCode());

        List<Plan> planList = planRepository.findByCriteria(criteria);

        if (planList == null || planList.isEmpty()) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "计划名称不存在" + planName);
        }
        return planList.get(0);
    }


    public Policy getPolicy(String policyNo) {
        if (policyNo == null || policyNo.isBlank()) {
            throw new TpaBizException(BizErrorCode.ADJUSTMENT_ERROR, "该赔案未关联保单");
        }
        Criteria<Policy> criteria = Criteria.create();
        criteria.eq(Policy::getPolicyNo, policyNo);

        List<Policy> policyList = policyRepository.findByCriteria(criteria);

        if (policyList == null || policyList.isEmpty()) {
            throw new TpaBizException(BizErrorCode.ADJUSTMENT_ERROR, "该赔案关联保单不存在");
        }
        return policyList.get(0);
    }


    public List<Coverage> getCoverageList(Long planId, String planVersion) {
        Criteria<Coverage> criteria = Criteria.create();
        criteria.eq(Coverage::getPlanId, planId);
        criteria.eq(Coverage::getVersion, planVersion);

        List<Coverage> coverageList = coverageRepository.findByCriteria(criteria);

        if (coverageList == null || coverageList.isEmpty()) {
            throw new DataNotFoundException("未找到该计划的险种  planId：" + planId);
        }

        return coverageList;
    }


    public List<LiabilityConfig> getLiabilityList(Long planId, String planVersion) {
        Criteria<Liability> criteria = new Criteria<>();
        criteria.eq(Liability::getPlanId, planId);
        criteria.eq(Liability::getVersion, planVersion);

        //获取该计划的所有责任
        List<Liability> liabilityList = liabilityRepository.findByCriteria(criteria);

        if (liabilityList == null || liabilityList.isEmpty()) {
            throw new DataNotFoundException("未找到该计划的责任  planId：" + planId);
        }

        return tranferToConfigList(liabilityList);
    }


    public List<LiabilityConfig> tranferToConfigList(List<Liability> liabilityList) {
        List<LiabilityConfig> liabilityConfigList = liabilityList.stream()
                .map(liabilityConverter::toLiabilityConfig)
                .collect(Collectors.toList());

        return liabilityConfigList;
    }


    public List<LiabilitySharing> getSharingList(Long planId, String planVersion) {
        Criteria<LiabilitySharing> criteria = new Criteria<>();
        criteria.eq(LiabilitySharing::getPlanId, planId);
        criteria.eq(LiabilitySharing::getVersion, planVersion);

        //获取该计划的所有责任
        List<LiabilitySharing> sharingList = liabilitySharingRepository.findByCriteria(criteria);

        return sharingList;
    }


    public List<LiabilitySharingRelation> getSharingRelationList(Long planId, String planVersion) {
        Criteria<LiabilitySharingRelation> criteria = new Criteria<>();
        criteria.eq(LiabilitySharingRelation::getPlanId, planId);
        criteria.eq(LiabilitySharingRelation::getVersion, planVersion);

        //获取该计划的所有责任
        List<LiabilitySharingRelation> sharingRelationList = liabilitySharingRelationRepository.findByCriteria(criteria);

        return sharingRelationList;
    }


//    /**
//     * 筛选并去除是后付责任的责任
//     *
//     * @param inputList
//     * @return
//     */
//    public List<LiabilityConfig> filterNextLiability(List<LiabilityConfig> inputList) {
//
//        List<String> nextUuidList = inputList.stream().map(LiabilityConfig::getNextLiabilityUuid)
//                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
//
//        List<LiabilityConfig> outputList = new ArrayList<>();
//
//        for (LiabilityConfig liabilityConfig : inputList) {
//            if (!nextUuidList.contains(liabilityConfig.getUuid())) {
//                outputList.add(liabilityConfig);
//            }
//        }
//
//        return outputList;
//    }


    /**
     * 将前端给的绑定关系的字符串转化为uuid的字符串
     *
     * @return
     */
    public String transferToLiabilityUuid(List<LiabilityToBind> liabilityToBindList) {
        if (liabilityToBindList == null) {
            return null;
        }

        List<String> uuidList = new ArrayList<>();
        for (LiabilityToBind liability : liabilityToBindList) {
            if (liability != null && liability.getUuid() != null) {
                uuidList.add(liability.getUuid());
            }
        }

        String result = uuidList.stream()
                .filter(str -> str != null && !str.isEmpty())
                .collect(Collectors.joining(","));

        return result;
    }


    /**
     * 将存储的uuid字符串转化为绑定关系的字符串
     *
     * @return
     */
    public List<LiabilityToBind> transferFromLiabilityUuid(String uuidListString, Long planId) {
        if (uuidListString == null || uuidListString.isBlank()) {
            return new ArrayList<>();
        }

        try {
            String[] uuidList = uuidListString.split(",");

            List<LiabilityConfig> liabilityConfigList = liabilityService.queryForBind(planId);

            //uuid, liability
            Map<String, LiabilityConfig> liabilityConfigMap = liabilityConfigList.stream().collect(Collectors.toMap(LiabilityConfig::getUuid, t -> t));

            log.error(liabilityConfigMap.toString());

            List<LiabilityToBind> liabilityToBindList = new ArrayList<>();
            for (String uuid : uuidList) {
                if (liabilityConfigMap.containsKey(uuid)) {
                    liabilityToBindList.add(liabilityShortConverter(liabilityConfigMap.get(uuid), null));
                } else {
                    LiabilityConfig deletedLiability = liabilityService.queryDeletedLiability(uuid);

                    if (deletedLiability != null) {
                        LiabilityToBind liabilityToBind = liabilityShortConverter(deletedLiability, null);
                        liabilityToBind.setActive(LiabilityToBindStatusEnum.DELETED.getCode());

                        liabilityToBindList.add(liabilityToBind);
                    } else {
                        LiabilityToBind liabilityToBind = new LiabilityToBind();
                        liabilityToBind.setUuid(uuid);
                        liabilityToBind.setName("");
                        liabilityToBind.setActive(LiabilityToBindStatusEnum.DELETED.getCode());

                        liabilityToBindList.add(liabilityToBind);
                    }
                }
            }

            return liabilityToBindList;
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }


    public LiabilityToBind liabilityShortConverter(LiabilityConfig liabilityConfig, VisitTypeEnum visitType) {
        LiabilityToBind result = new LiabilityToBind();
        result.setName(liabilityConfig.getLiabilityName());
        result.setUuid(liabilityConfig.getUuid());

        if (visitType == null || !liabilityConfig.getLiabilityType().equals(LiabilityTypeEnum.REIMBURSEMENT)) {
            result.setActive(LiabilityToBindStatusEnum.ACTIVE.getCode());
        } else if (liabilityConfig.getRestrictOutInsure() == null) {
            result.setActive(LiabilityToBindStatusEnum.INACTIVE.getCode());
        } else if (liabilityConfig.getRestrictOutInsure().getVisitType().contains(visitType.getCode())) {
            result.setActive(LiabilityToBindStatusEnum.ACTIVE.getCode());
        } else {
            result.setActive(LiabilityToBindStatusEnum.INACTIVE.getCode());
        }

        return result;
    }
}
