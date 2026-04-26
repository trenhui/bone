package com.bone.tpa.intelligent.adjustment.service;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.core.util.PkListUtil;
import com.bone.tpa.intelligent.adjustment.converter.LiabilityShareConvert;
import com.bone.tpa.intelligent.adjustment.dto.LiabilitySharingRelationDTO;
import com.bone.tpa.sdk.adjustment.model.Liability;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.sdk.dao.impl.LiabilitySharingRelationRepository;
import com.bone.tpa.sdk.adjustment.model.LiabilitySharingRelation;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.service.LiabilityInfoBasicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LiabilityShareReleationService {
    @Autowired
    private  LiabilitySharingRelationRepository mapper;


    @Autowired
    private PlanService planService;

    @Autowired
    private LiabilityShareConvert convert;

    @Autowired
    private LiabilityService liabilityService;


    @Transactional(rollbackFor = Throwable.class)
    public void create(List<LiabilitySharingRelationDTO> dtoList){
        List<LiabilitySharingRelation> entityList =   convert.toRelationEntityList(dtoList);
        List<String> policyNoList = dtoList.stream().map(LiabilitySharingRelationDTO::getPolicyNo).distinct().toList();
        if(policyNoList.size()>1){
            throw new IllegalArgumentException("policyNo is not unique");
        }
        String policyNo = PkListUtil.first(policyNoList);
        // 需要校验共保不能跨计划
        List<LiabilitySharingRelationDTO>  existList =  getDraftListByPolicyNo(policyNo);
        Map<String,Long> codePlanMap = existList.stream().collect(Collectors.toMap(LiabilitySharingRelationDTO::getShareCode, LiabilitySharingRelationDTO::getPlanId, (oldValue, newValue) -> oldValue));

        for(LiabilitySharingRelation entity:entityList){
            entity.setVersion(null);
            if(entity.getPlanId() == null){
                throw new IllegalArgumentException("planId is null");
            }
            planService.checkPlanDraft(entity.getPlanId());
            if(entity.getShareCode() == null){
                throw new IllegalArgumentException("shareCode is null");
            }
            if(entity.getPolicyNo() == null){
                throw new IllegalArgumentException("policyNo is null");
            }
            if(entity.getLiabilityUuid() == null){
                throw new IllegalArgumentException("liabilityUuid is null");
            }
            if(codePlanMap.get(entity.getShareCode())!=null){
                if(!codePlanMap.get(entity.getShareCode()).equals(entity.getPlanId())){
                    throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "不能绑定不同的计划, 计划id应为: " + entity.getPlanId());
                }
            }
        }

        //要检查单个责任是不是已经有两个共保关系了
        Map<String, Long> liabilityCountMap = existList.stream().collect(Collectors.groupingBy(LiabilitySharingRelationDTO::getLiabilityUuid, Collectors.counting()));
        for (LiabilitySharingRelation entity:entityList) {
            Long count = liabilityCountMap.get(entity.getLiabilityUuid());
            if (count != null && count >= 2) {
                LiabilityConfig liability = liabilityService.queryByUuidPlanId(entity.getPlanId(), entity.getLiabilityUuid());

                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "责任的共保关系已经达到上限: " + liability.getLiabilityName());
            }
        }

        //要检查这责任是不是已经和这个共保关系有关联了
        Map<String, List<String>> existMap = existList.stream().collect(Collectors.groupingBy(LiabilitySharingRelationDTO::getLiabilityUuid, Collectors.mapping(LiabilitySharingRelationDTO::getShareCode, Collectors.toList())));
        for (LiabilitySharingRelation entity:entityList) {
            List<String> shareList = existMap.get(entity.getLiabilityUuid());
            if (shareList != null && shareList.contains(entity.getShareCode())) {
                LiabilityConfig liability = liabilityService.queryByUuidPlanId(entity.getPlanId(), entity.getLiabilityUuid());

                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "该责任已经匹配此共保关系: " + liability.getLiabilityName());
            }
        }

        for(LiabilitySharingRelation entity:entityList){
            mapper.insert(entity);
        }

    }

    public void delete(Long id ){
        LiabilitySharingRelation entity =  mapper.findById(id);
        if (entity == null) {
            throw new TpaBizException(BizErrorCode.NO_RECORD, String.valueOf(id));
        }
        planService.checkPlanDraft(entity.getPlanId());
        mapper.deleteById(id);
    }

    public List<LiabilitySharingRelationDTO> getListByPlanId(Long planId) {
        Criteria<LiabilitySharingRelation> criteria = new Criteria();
        criteria.eq(LiabilitySharingRelation::getPlanId,planId);
        criteria.addSort(Criteria.addSortWithTableField("plan_id","share_code","id"));
        return toDtoList(mapper.findByCriteria(criteria));
    }

    public List<LiabilitySharingRelationDTO> getDraftListByPolicyNo(String policyNo) {
        Criteria<LiabilitySharingRelation> criteria = new Criteria();
        criteria.isNull(LiabilitySharingRelation::getVersion);
        criteria.eq(LiabilitySharingRelation::getPolicyNo,policyNo);
        criteria.addSort(Criteria.addSortWithTableField("plan_id","share_code","id"));
        return toDtoList(mapper.findByCriteria(criteria));
    }

    private List<LiabilitySharingRelationDTO> toDtoList(List<LiabilitySharingRelation> entityList) {
        if(PkListUtil.isEmpty(entityList)){
            return PkListUtil.newArrayList();
        }

        List<LiabilitySharingRelationDTO>  rs =  convert.toRelationDtoList(entityList);
        return  rs;
    }
}
