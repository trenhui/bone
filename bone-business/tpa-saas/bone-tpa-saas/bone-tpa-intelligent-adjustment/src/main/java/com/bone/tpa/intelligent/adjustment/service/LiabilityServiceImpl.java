package com.bone.tpa.intelligent.adjustment.service;

import com.bone.core.exception.BizException;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.criteria.SortItem;
import com.bone.metadata.sdk.query.criteria.SqlSortTypeEnums;
import com.bone.metadata.sdk.dto.MetaFieldDTO;
import com.bone.core.util.PkListUtil;
import com.bone.tpa.sdk.adjustment.enums.PolicyConfigStatusEnum;
import com.bone.tpa.sdk.adjustment.model.Plan;
import com.bone.tpa.sdk.adjustment.model.Policy;
import com.bone.tpa.sdk.service.LiabilityConverter;
import com.bone.tpa.intelligent.adjustment.dto.request.ChainLiabilityRequest;
import com.bone.tpa.intelligent.adjustment.dto.request.LiabilityCreateBatchRequest;
import com.bone.tpa.intelligent.adjustment.dto.request.LiabilityCreateRequest;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.sdk.dao.impl.LiabilityRepository;
import com.bone.tpa.intelligent.adjustment.util.StringUtil;
import com.bone.tpa.sdk.adjustment.model.Liability;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.service.LiabilityInfoBasicService;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LiabilityServiceImpl implements LiabilityService {

    @Autowired
    private   LiabilityRepository liabilityMapper;

    @Autowired
    private LiabilityConverter convert;


    @Autowired
    private PlanService planService;

    @Lazy
    @Autowired
    private LiabilityInfoService liabilityInfoService;


    @Override
    public void checkPolicyConfigStatus(String policyNo) {
        Policy policy = liabilityInfoService.getPolicy(policyNo);

        if (policy.getConfigStatus().equals(PolicyConfigStatusEnum.ACTIVE.getCode())) {
            throw new TpaBizException(BizErrorCode.INNER_PARAMETER_ERROR, "保单" + policyNo + "处于启用状态！");
        }
    }

    @Override
    public void checkPolicyConfigStatus(Long planId) {
        Plan plan = liabilityInfoService.getplan(planId);

        checkPolicyConfigStatus(plan.getPolicyNo());
    }

    @Override
    public List<Liability> queryByPlanIds(List<Long> planIds) {
        Criteria<Liability> criteria= new Criteria<>();
        criteria.addSort(Criteria.getDefaultIdSort());
        criteria.in(Liability::getPlanId, planIds);
        List<Liability> list = liabilityMapper.findByCriteria(criteria);
        return list;
    }

    @Override
    public List<Liability> getByPlanId(Long planId) {
        Criteria<Liability> criteria= new Criteria<>();
        criteria.addSort(Criteria.getDefaultIdSort());
        criteria.eq(Liability::getPlanId, planId);
        List<Liability> list = liabilityMapper.findByCriteria(criteria);
        return list;
    }


    @Override
    public LiabilityConfig findById(Long id) {
        return toDto(liabilityMapper.findById(id));
    }

    /**
     * 创建责任
     *
     * @param
     */
    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void create(LiabilityCreateBatchRequest batchRequest) {
        for(LiabilityCreateRequest request : batchRequest.getLiabilityCreateRequestList()){
            createOne(request, batchRequest.getPolicyNo());
        }
    }

    @Override
    public void update(LiabilityConfig config) {
        Liability entity =     toEntity(config);
        if(entity.getId() == null){
            throw new IllegalArgumentException("id is null");
        }

        Liability exist =    liabilityMapper.findById(Long.valueOf(config.getId()));
        if(exist == null){
            throw new IllegalArgumentException("id is not exist");
        }

        planService.checkPlanDraft(exist.getPlanId());

        liabilityMapper.update(entity);

    }

    private void createOne(LiabilityCreateRequest request, String policyNo){
        LiabilityConfig liabilityDTO  = new LiabilityConfig();
        liabilityDTO.setPlanId(request.getPlanId());
        liabilityDTO.setPolicyNo(policyNo);
        liabilityDTO.setLiabilityName(request.getLiabilityName());
        liabilityDTO.setCoverageId(request.getCoverageId());
        Liability entity = toEntity(liabilityDTO);
        entity.setUuid(UUID.randomUUID().toString());
        if(entity.getPlanId()== null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "planId is null");
        }
        if(entity.getPolicyNo()== null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "policyNo is null");
        }
     /*   if(entity.getLiabilityCode() == null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "liabilityCode is null");
        }*/
        if(entity.getCoverageId()== null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "coverage is null");
        }
        StringUtil.stringInvalid(entity.getLiabilityName(), "责任名称");

        List<String> liabilityNameList = existLiabilityName(entity.getCoverageId());
        if (liabilityNameList.contains(entity.getLiabilityName())) {
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "同保单计划险种下不允许有重复的责任名称: " + entity.getLiabilityName());
        }

        liabilityMapper.insert(entity);

    }

    private List<String> existLiabilityName(Long coverageId) {
        Criteria<Liability> criteria= new Criteria<>();
        criteria.eq(Liability::getCoverageId, coverageId);

        List<Liability> liabilityList = liabilityMapper.findByCriteria(criteria);

        return liabilityList.stream().map(Liability::getLiabilityName).toList();
    }


    private Liability toEntity(LiabilityConfig dto){
        Liability  entity = convert.toLiability(dto);
        return  entity;
    }

    @Override
    public List<LiabilityConfig> queryByPlanId(Long planId) {
        if(planId == null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "planId is null");
        }
        Criteria<Liability> criteria= new Criteria<>();
        criteria.addSort(Criteria.getDefaultIdSort());
        criteria.eq(Liability::getPlanId,planId);
        List<Liability> dbList =    liabilityMapper.findByCriteria(criteria);
        return toDtoList(dbList);
    }

    @Override
    public List<LiabilityConfig> queryDraftListByPolicyNo(String policyNo) {
        Criteria<Liability> criteria= new Criteria<>();
        criteria.isNull(Liability::getVersion);
        criteria.eq(Liability::getPolicyNo,policyNo);
        List<Liability> dbList =    liabilityMapper.findByCriteria(criteria);
        return toDtoList(dbList);
    }

    @Override
    public LiabilityConfig  queryByUuidPlanId(Long planId, String liabilityUuid) {
        return PkListUtil.first(queryByPlanId(planId).stream().filter(t->t.getUuid().equals(liabilityUuid)).collect(Collectors.toList()));
    }

    @Override
    public List<LiabilityConfig> queryForBind(Long planId) {
        if(planId == null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "planId is null");
        }
        Criteria<Liability> criteria= new Criteria<>();
        criteria.addSort(Criteria.getDefaultIdSort());
        criteria.eq(Liability::getPlanId,planId);
        criteria.eq(Liability::getInvoiceRelateAble, Boolean.TRUE);
        List<Liability> dbList =    liabilityMapper.findByCriteria(criteria);
        return toDtoList(dbList);
    }


    @Override
    public LiabilityConfig queryDeletedLiability(String liabilityUuid) {
        if(liabilityUuid == null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "uuid is null");
        }
        Criteria<Liability> criteria= new Criteria<>();
        criteria.eq(Liability::getUuid, liabilityUuid);
        criteria.setLimit(1);
        criteria.setOffset(0);
        criteria.setSortWords("order by id desc");

        List<Liability> dbList =    liabilityMapper.findByCriteria(criteria, true);

        if (dbList == null || dbList.isEmpty()) {
            return null;
        }

        return toDto(dbList.get(0));
    }


    /**
     * 获取先后赔付责任的map
     *
     * @param planId
     * @return
     */
    @Override
    public Map<Long, List<LiabilityConfig>> chainLiabilityMap(Long planId) {
        Map<Long, List<LiabilityConfig>> rs = new HashMap<>();
        List<LiabilityConfig> allList =  queryByPlanId(planId);

        List<String> afterUuidIdList = allList.stream().filter(t->t.getNextLiabilityUuid()!= null)
                .map(LiabilityConfig::getNextLiabilityUuid).collect(Collectors.toList());
        if(PkListUtil.isEmpty(afterUuidIdList)){
            return rs;
        }
        Map<String,LiabilityConfig > afterUuidDtoMap  = new HashMap<>() ;
        for(String uuid : afterUuidIdList){
            LiabilityConfig config =  queryByUuidPlanId(planId,uuid);
            if( config == null){
                throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "后赔付责任不存在: "+uuid);
            }
            afterUuidDtoMap.put(uuid,config);
        }

        List<LiabilityConfig> firstHeadList = allList.stream().
                filter(t->!afterUuidDtoMap.containsKey(t.getUuid()) && t.getNextLiabilityUuid()!= null)
                .collect(Collectors.toList());
        if(PkListUtil.isEmpty(firstHeadList)){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "计划不存在先赔责任");
        }
        for(LiabilityConfig first : firstHeadList){
            List<Long> existIdInChainList = PkListUtil.newArrayList();
            String firstName = first.getLiabilityName();

            List<LiabilityConfig> chainList = PkListUtil.newArrayList();
            rs.put(first.getId(),chainList);
            while(first != null){
                if(existIdInChainList.size()==3){
                    throw new BizException(BizErrorCode.PARAMETER_ERROR.getCode(), "责任链长度超过3层,首责任名称: "+firstName);
                }
                if(existIdInChainList.contains(first.getId())){
                    throw new BizException(BizErrorCode.PARAMETER_ERROR.getCode(), "责任链存在循环闭环,首责任名称: "+firstName);
                }
                existIdInChainList.add(first.getId());
                chainList.add(first);
                first = afterUuidDtoMap.get(first.getNextLiabilityUuid());
            }
        }

        return rs;
    }

    /**
     * 创建先后责任
     *
     * @param request
     */
    @Transactional(rollbackFor = Throwable.class)
    @Override
    public void saveChainLiability(ChainLiabilityRequest request) {
        if(request.getId() == null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "id不能为空");
        }
        LiabilityConfig config =   findById(request.getId());
        if( config.getNextLiabilityUuid() != null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "当前责任已经存在后赔付责任");
        }
        //校验计划状态
        checkPolicyConfigStatus(config.getPolicyNo());
        planService.checkPlanDraft(Long.valueOf(config.getPlanId()));

        if(request.getNextUuid() == null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "后赔付责任不能为空");
        }
        if(request.getNextType() == null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "后赔付责任类型不能为空");
        }

        LiabilityConfig nextConfig =    queryByUuidPlanId(Long.valueOf(config.getPlanId()),request.getNextUuid());

        if( nextConfig == null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "后赔付责任不存在");
        }
        if(config.getId().equals(nextConfig.getId())){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "不能自动绑定自己");
        }
        Liability updateDto = new Liability();
        updateDto.setId(request.getId());
        updateDto.setNextLiabilityUuid(request.getNextUuid());
        updateDto.setNextLiabilityType(request.getNextType());
        liabilityMapper.update(updateDto);
        //校验循环依赖和多层嵌套（如果有问题，会方法内报错）
        Map<Long, List<LiabilityConfig>> chainMap =  chainLiabilityMap(Long.valueOf(config.getPlanId()));
        updateRelaAble(Long.valueOf(config.getPlanId()),chainMap);
    }

    /**
     * 更新chainMap
     * @param planId
     * @param chainMap
     */
    private void updateRelaAble(Long planId,  Map<Long, List<LiabilityConfig>> chainMap){
        List<LiabilityConfig>  allList =  queryByPlanId(planId);
        for(LiabilityConfig config : allList){
            Liability updateDto = new Liability();
            updateDto.setId(Long.valueOf(config.getId()));
            updateDto.setInvoiceRelateAble(Boolean.TRUE);
            liabilityMapper.update(updateDto);
        }

        chainMap.entrySet().stream().forEach(t->{
            List<LiabilityConfig> chainList =  t.getValue();
            for(int i =0;i<chainList.size();i++){
                if(i == 0){
                    continue;
                }
                LiabilityConfig backConfig =  chainList.get(i);
                Liability updateDto = new Liability();
                updateDto.setId(Long.valueOf(backConfig.getId()));
                updateDto.setInvoiceRelateAble(Boolean.FALSE);
                liabilityMapper.update(updateDto);
            }
        });
    }


    /**
     * 删除先后责任
     *
     * @param id
     */
    @Override
    public void deleteChainLiability(Long id) {
        if(id == null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "id不能为空");
        }
        LiabilityConfig config =   findById(id);
        //校验计划状态
        checkPolicyConfigStatus(config.getPolicyNo());
        planService.checkPlanDraft(Long.valueOf(config.getPlanId()));

        Liability updateDto = new Liability();
        updateDto.setId(id);
        updateDto.setUuid(config.getUuid());
        liabilityMapper.updateAndClearFields(updateDto,PkListUtil.asList("next_liability_uuid",
                "next_liability_type"));

        Map<Long, List<LiabilityConfig>> chainMap =  chainLiabilityMap(Long.valueOf(config.getPlanId()));
        updateRelaAble(Long.valueOf(config.getPlanId()),chainMap);

    }

    /**
     * 发布之前的校验
     *
     * @param liabilityList
     */
    @Override
    public void checkForDeploy( List<Liability> liabilityList) {
        List<LiabilityConfig> configList = toDtoList(liabilityList);
        if(PkListUtil.isEmpty(configList)){
            return;
        }
        for(LiabilityConfig config : configList){
            checkForDeployOne(config);
        }
    }

    /**
     * 发布时候，校验一个责任是否合法
     * @param config
     */
    private void checkForDeployOne(LiabilityConfig config ){
        if(config.getPlanId() == null){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "计划id不能为空");
        }
    }

    private List<LiabilityConfig> toDtoList( List<Liability> dbList){
        List<LiabilityConfig> rs = Lists.newArrayList();
        for(Liability entity:dbList){
            rs.add(toDto(entity));
        }

        return rs;
    }

    private  LiabilityConfig toDto(Liability entity){
        if(entity == null){
            return  null;
        }
        LiabilityConfig dto = convert.toLiabilityConfig(entity);
        return dto;
    }



}
