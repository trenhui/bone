package com.bone.tpa.intelligent.adjustment.service;

import cn.hutool.core.util.IdUtil;
import com.bone.core.tenant.TenantAbstractEntity;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.criteria.SortItem;
import com.bone.metadata.sdk.dto.MetaFieldDTO;
import com.bone.core.util.PkListUtil;
import com.bone.core.util.SnowUtil;
import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.core.redis.RedisLockManage;
import com.bone.tpa.intelligent.adjustment.converter.PlanConvert;
import com.bone.tpa.intelligent.adjustment.dto.PlanDTO;
import com.bone.tpa.intelligent.adjustment.util.AdjustUtil;
import com.bone.tpa.intelligent.adjustment.util.StringUtil;
import com.bone.tpa.sdk.adjustment.enums.PlanStatus;
import com.bone.tpa.sdk.adjustment.model.*;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.dao.impl.*;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlanServiceImpl  implements PlanService {
    @Autowired
    private PlanRepository planMapper;

    @Autowired
    private AdjustUtil adjustUtil;

    @Autowired
    private CoverageRepository coverageMapper;
    @Autowired
    private LiabilitySharingRelationRepository shareingRelationMapper;


    @Autowired
    private LiabilitySharingRepository sharingMapper;


    @Autowired
    private LiabilityRepository liabilityMapper;

    @Autowired
    private PlanConvert convert;

    @Autowired
    @Lazy
    private LiabilityService liabilityService;

    /**
     * 创建计划
     *
     * @param planDTO
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public PlanDTO createPlan(PlanDTO planDTO) {
        Plan plan =  convert.toEntity(planDTO);


        if(StringUtils.isBlank(planDTO.getPolicyNo())){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "保单号不能为空");
        }
        StringUtil.stringRegexCheck(plan.getPlanName(),StringUtil.planNameMatcher, "请正确输入计划名称，2-20字，中文字母数字下划线");
        StringUtil.stringRegexCheck(plan.getPlanCode(),StringUtil.planCodeMatcher, "请正确输入计划code，字母数字下划线");

        if(plan.getPlanLimit() == null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "计划限额不能为空");
        }
        if(plan.getPlanLimit().compareTo(BigDecimal.ZERO) == 0){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "计划限额不能为0");
        }
        List<String> planNameList = existPlanName(plan.getPolicyNo());
        if (planNameList.contains(plan.getPlanName())) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "同保单下不允许有重复的计划名称: " + plan.getPlanName());
        }

       /* List<String> existPlanCodeList = existPlanCode(plan.getPolicyNo());
        if (existPlanCodeList.contains(plan.getPlanCode())) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "同保单下不允许有重复的计划Code: " + plan.getPlanCode());
        }*/
        plan.setStatus(PlanStatus.DRAFT.getCode());
        plan.setVersion(null);
        plan.setUuid(UUID.randomUUID().toString());
        plan.setSortNumber(IdUtil.getSnowflake(SnowUtil.getWorkId(), 1).nextId());
        Long id = planMapper.insert(plan);
        return convert(planMapper.findById(id));
    }

    @Transactional(rollbackFor = Exception.class)

    @Override
    public List<PlanDTO> createPlanList(List<PlanDTO> planDTOList) {
        List<PlanDTO> rs  = Lists.newArrayList();
        for(PlanDTO planDTO : planDTOList){
            rs.add(createPlan(planDTO));
        }
        return rs;
    }

    /**
     * 更新计划
     *
     * @param planDTO
     * @return
     */
    @Override
    public PlanDTO updatePlan(PlanDTO planDTO) {
        Plan plan =  convert.toEntity(planDTO);
        if(plan.getId() == null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "计划id不能为空");
        }
        Plan exist =   planMapper.findById(plan.getId());
        if( exist == null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "计划不存在");
        }
        if( !exist.getStatus().equals(PlanStatus.DRAFT.getCode())){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "计划状态不允许修改");
        }


        StringUtil.stringRegexCheck(plan.getPlanName(),StringUtil.planNameMatcher, "请正确输入计划名称，2-20字，中文字母数字下划线");
        StringUtil.stringRegexCheck(plan.getPlanCode(),StringUtil.planCodeMatcher, "请正确输入计划code，字母数字下划线");


        if(plan.getPlanLimit() == null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "计划限额不能为空");
        }
        if(plan.getPlanLimit().compareTo(BigDecimal.ZERO) == 0){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "计划限额不能为0");
        }

        List<Plan> planExistOtherList =    getDraftPlanListByPolicyNo(plan.getPolicyNo());
        //要排除自己
        planExistOtherList =   planExistOtherList.stream().filter(t->!t.getId().equals(plan.getId())).collect(Collectors.toList());
        List<String> planNameList =planExistOtherList.stream().map(Plan::getPlanName).toList();
        if (planNameList.contains(plan.getPlanName())) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "该保单下已有计划: " + plan.getPlanName());
        }

        exist.setPlanCode(plan.getPlanCode());
        exist.setPlanName(plan.getPlanName());
        exist.setPlanLimit(plan.getPlanLimit());
        planMapper.update(exist);
        return findById(exist.getId());
    }

    @Override
    public PlanDTO findById(Long id) {
        return convert(planMapper.findById(id));
    }

    @Override
    public List<PlanDTO> findByIdList(List<Long> idList) {
        if(PkListUtil.isEmpty(idList)){
            return PkListUtil.newArrayList();
        }
        Criteria<Plan> criteria= new Criteria<>();
        criteria.in(Plan::getId,idList);
        List<Plan> planList =    planMapper.findByCriteria(criteria);
        return convert(planList);
    }

    @Override
    public List<PlanDTO> queryByPolicyNo(String policyNo, PlanStatus statusEm) {
        Criteria<Plan> criteria= new Criteria<>();
        SortItem sortItem = new SortItem();
        MetaFieldDTO sortField = new MetaFieldDTO();
        sortField.setFieldName("sort_number");
        sortItem.addItem(sortField);

        criteria.addSort(sortItem);
        criteria.eq(Plan::getPolicyNo,policyNo);
        criteria.eq(Plan::getStatus,statusEm.getCode());

        List<Plan> planList =    planMapper.findByCriteria(criteria);
        return convert(planList);
    }

    @Override
    public PlanDTO queryByPlanUuid(String planUuid) {
        Criteria<Plan> criteria= new Criteria<>();

        criteria.eq(Plan::getUuid, planUuid);
        criteria.eq(Plan::getStatus, PlanStatus.ACTIVE.getCode());

        List<Plan> planList = planMapper.findByCriteria(criteria);

        if (planList == null || planList.isEmpty()) {
            return null;
        }

        return convert(planList.get(0));
    }


    private List<Plan> getDraftPlanListByPolicyNo(String policyNo) {
        Criteria<Plan> criteria= new Criteria<>();
        SortItem sortItem = new SortItem();
        MetaFieldDTO sortField = new MetaFieldDTO();
        sortField.setFieldName("sort_number");
        sortItem.addItem(sortField);

        criteria.addSort(sortItem);
        criteria.eq(Plan::getPolicyNo,policyNo);
        criteria.eq(Plan::getStatus, PlanStatus.DRAFT.getCode());

        List<Plan> planList =    planMapper.findByCriteria(criteria);
        return planList;
    }

    private List<String> existPlanName(String policyNo) {

        List<Plan> planList =    getDraftPlanListByPolicyNo(policyNo);

        return planList.stream().map(Plan::getPlanName).toList();
    }

    private List<String> existPlanCode(String policyNo) {
        List<Plan> planList =    getDraftPlanListByPolicyNo(policyNo);
        return planList.stream().map(Plan::getPlanCode).toList();
    }

    /**
     * @param planId
     * @return
     */
    @Override
    public void checkPlanDraft(Long planId) {
        if(planId == null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "计划id不能为空");
        }
        PlanDTO planDTO=  findById(planId);
        if( planDTO == null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "计划不存在");
        }
        if(!planDTO.getStatus().equals(PlanStatus.DRAFT.getCode())){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "计划状态不允许修改");
        }
    }

    @Transactional(rollbackFor = Throwable.class)
    @Override
    public Boolean deployPlan(Long planId) {
        checkPlanDraft(planId);
        Plan plan = planMapper.findById(planId);
        PlanDTO currentPlanActive =   currentActivePlan(plan.getUuid());
        if( currentPlanActive != null){
            Plan updateStatusEntity = new Plan();
            updateStatusEntity.setId(Long.valueOf(currentPlanActive.getId()));
            updateStatusEntity.setStatus(PlanStatus.INACTIVE.getCode());
            planMapper.update(updateStatusEntity);
        }
        //激活计划
        activePlan(plan);
        //复制计划到草稿
        copyDraftFromPlan(plan.getId());
        return true;
    }

    @Override
    public void rollBackFromPlan(Long planId) {
        PlanDTO source =   findById(planId);
        if( source == null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "计划不存在");
        }
        if(source.getStatus().equals(PlanStatus.DRAFT.getCode())){
           throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "计划状态不允许回滚");
        }
        Criteria<Plan> criteria = new Criteria<>();
        criteria.eq(Plan::getPlanCode,source.getUuid());
        criteria.eq(Plan::getPolicyNo,source.getPolicyNo());
        criteria.eq(Plan::getStatus,PlanStatus.DRAFT.getCode());
        Plan planDraft = PkListUtil.first(    planMapper.findByCriteria(criteria));
        if( planDraft != null){
            Long draftPlanId = planDraft.getId();
            deleteAllByPlanId(draftPlanId);
        }
        copyDraftFromPlan(planId);
    }


    private void deleteAllByPlanId(Long planId){
        planMapper.deleteById(planId);
        coverageMapper.findByCriteria(new Criteria<Coverage>().eq(Coverage::getPlanId,planId))
                .forEach(coverage -> coverageMapper.deleteById(coverage.getId()));
        liabilityMapper.findByCriteria(new Criteria<Liability>().eq(Liability::getPlanId,planId))
                .forEach(liability -> liabilityMapper.deleteById(liability.getId()));
        sharingMapper.findByCriteria(new Criteria<LiabilitySharing>().eq(LiabilitySharing::getPlanId,planId))
                .forEach(sharing -> sharingMapper.deleteById(sharing.getId()));
        shareingRelationMapper.findByCriteria(new Criteria<LiabilitySharingRelation>().eq(LiabilitySharingRelation::getPlanId,planId))
                .forEach(relation -> shareingRelationMapper.deleteById(relation.getId()));

    }


    private void activePlan(Plan plan ){
        Plan updateStatusEntity = new Plan();
        updateStatusEntity.setId(plan.getId());
        Long version =  adjustUtil.getIdIncreaseWithDateByRedis("adjust_plan_version_"+plan.getUuid());
        updateStatusEntity.setStatus(PlanStatus.ACTIVE.getCode());
        updateStatusEntity.setVersion(version.toString());
        planMapper.update(updateStatusEntity);
        List<Coverage> coverageList = coverageMapper.findByCriteria(new Criteria<Coverage>().eq(Coverage::getPlanId,plan.getId()));
        for(Coverage coverage : coverageList){
            coverage.setVersion(version.toString());
             coverageMapper.update(coverage);
        }

        List<Liability> liabilityList =      liabilityMapper.findByCriteria(new Criteria<Liability>().eq(Liability::getPlanId,plan.getId()));
        //
        SpringContextUtils.getBean(LiabilityService.class).checkForDeploy(liabilityList);
        for(Liability liability : liabilityList){
            if (liability.getRestrictOutInsure() == null || liability.getLiabilityType() == null || liability.getAccountType() == null
                    || liability.getQuotaController() == null || liability.getLiabilityLimit() == null || liability.getAdjustmentDetail() == null) {
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "责任 " + liability.getLiabilityName() + " 的设置不正确，请重新设置和发布。");
            }
            liability.setVersion(version.toString());
             liabilityMapper.update(liability);
        }
        List<LiabilitySharing> sharingList =     sharingMapper.findByCriteria(new Criteria<LiabilitySharing>().eq(LiabilitySharing::getPlanId,plan.getId()));
        for(LiabilitySharing share: sharingList){
            share.setVersion(version.toString());
            sharingMapper.update(share);
        }

        List<LiabilitySharingRelation> sharingRelationList =  shareingRelationMapper.findByCriteria(new Criteria<LiabilitySharingRelation>().eq(LiabilitySharingRelation::getPlanId,plan.getId()));
        for(LiabilitySharingRelation relation : sharingRelationList){
            relation.setVersion(version.toString());
            shareingRelationMapper.update(relation);
        }
    }

    public PlanDTO currentActivePlan(String uuid){
        Criteria<Plan> criteria= new Criteria<>();
        criteria.eq(Plan::getUuid,uuid);
        criteria.eq(Plan::getStatus,PlanStatus.ACTIVE.getCode());
        Plan plan = planMapper.findOneByCriteria(criteria);
        if(plan == null){
            return null;
        }
        return convert(plan);
    }

    /**
     *
     * z
     * @param sourcePlanId
     */
    private void copyDraftFromPlan(Long sourcePlanId){
        Plan plan =   planMapper.findById(sourcePlanId);
        cleanEntityBase(plan);
        plan.setVersion(null);
        plan.setStatus(PlanStatus.DRAFT.getCode());
        Long planNewId = planMapper.insert(plan);
        Map<Long,Long> coverIdMap = new HashMap<>();
        Map<Long,Long> liabilityIdMap  = new HashMap<>();
        Map<Long,Long> shareCodeIdMap  = new HashMap<>();

        //险种
        List<Coverage> coverageList = coverageMapper.findByCriteria(new Criteria<Coverage>().eq(Coverage::getPlanId,sourcePlanId));
        for(Coverage coverage : coverageList){
            Long oldId = coverage.getId();
            cleanEntityBase(coverage);
            coverage.setPlanId(planNewId);
            coverage.setVersion(null);
            Long newCoverId = coverageMapper.insert(coverage);
            coverIdMap.put(oldId,newCoverId);
        }
        //责任

        List<Liability> liabilityList =      liabilityMapper.findByCriteria(new Criteria<Liability>().eq(Liability::getPlanId,sourcePlanId));
        //
        for(Liability liability : liabilityList){
            Long oldId = liability.getId();
            cleanEntityBase(liability);
            liability.setPlanId(planNewId);
            liability.setCoverageId(coverIdMap.get(liability.getCoverageId()));
            liability.setVersion(null);
            Long newLiabId = liabilityMapper.insert(liability);
            liabilityIdMap.put(oldId,newLiabId);
        }
        List<LiabilitySharing> sharingList =     sharingMapper.findByCriteria(new Criteria<LiabilitySharing>().eq(LiabilitySharing::getPlanId,sourcePlanId));
        for(LiabilitySharing share: sharingList){
            Long oldId = share.getId();
            cleanEntityBase(share);
            share.setPlanId(planNewId);
            share.setVersion(null);
            sharingMapper.insert(share);
            shareCodeIdMap.put(oldId,share.getId());
        }

        List<LiabilitySharingRelation> sharingRelationList =  shareingRelationMapper.findByCriteria(new Criteria<LiabilitySharingRelation>().eq(LiabilitySharingRelation::getPlanId,sourcePlanId));
        for(LiabilitySharingRelation relation : sharingRelationList){
            cleanEntityBase(relation);
            relation.setPlanId(planNewId);
            relation.setVersion(null);
            shareingRelationMapper.insert(relation);
        }

    }


    private void cleanEntityBase(TenantAbstractEntity entity){
        entity.setId(null);
        entity.setUpdateBy(null);
        entity.setCreateBy(null);
        entity.setCreateTime(null);
        entity.setUpdateTime(null);
    }

    private PlanDTO convert(Plan plan){
        if(plan == null){
            return  null;
        }
        return PkListUtil.first(convert(PkListUtil.asList(plan)));
    }

    public List<PlanDTO> convert(List<Plan> inList){
        if(PkListUtil.isEmpty(inList)){
            return PkListUtil.newArrayList();
        }
        List<PlanDTO>  dtoList =  convert.toDtoList(inList);
        for(PlanDTO dto : dtoList){
            PlanStatus status =    PlanStatus.getByCode(dto.getStatus());
            if( status != null){
                dto.setStatusCn(status.getDesc());
            }
        }
        return dtoList;
    }

}
