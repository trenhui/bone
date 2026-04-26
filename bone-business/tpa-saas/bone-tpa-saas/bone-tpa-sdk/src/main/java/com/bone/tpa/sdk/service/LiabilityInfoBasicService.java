package com.bone.tpa.sdk.service;


import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.criteria.SortItem;
import com.bone.metadata.sdk.query.criteria.SqlSortTypeEnums;
import com.bone.metadata.sdk.dto.MetaFieldDTO;
import com.bone.tpa.sdk.adjustment.enums.PlanStatus;
import com.bone.tpa.sdk.adjustment.exception.DataNotFoundException;
import com.bone.tpa.sdk.adjustment.model.Liability;
import com.bone.tpa.sdk.adjustment.model.Plan;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.dao.impl.LiabilityRepository;
import com.bone.tpa.sdk.dao.impl.PlanRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LiabilityInfoBasicService {

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private LiabilityRepository liabilityMapper;

    @Autowired
    private LiabilityConverter convert;


    public Plan getPlan(String planUuid) {
        if (planUuid == null || planUuid.isBlank()) {
            throw new TpaBizException(BizErrorCode.ADJUSTMENT_ERROR, "该赔案未关联计划");
        }

        Criteria<Plan> criteria = Criteria.create();
        criteria.eq("uuid", planUuid);
        criteria.eq("status", "ACTIVE");

        List<Plan> planList = planRepository.findByCriteria(criteria);

        if (planList == null || planList.isEmpty()) {
            throw new TpaBizException(BizErrorCode.ADJUSTMENT_ERROR, "该赔案关联计划不存在");
        }
        return planList.get(0);
    }

    public List<LiabilityConfig> getLiabilityList(Long planId, String planVersion) {
        Criteria<Liability> criteria = Criteria.create();
        criteria.eq("planId", planId);
        criteria.eq("version", planVersion);

        //获取该计划的所有责任
        List<Liability> liabilityList = liabilityMapper.findByCriteria(criteria);

        if (liabilityList == null || liabilityList.isEmpty()) {
            throw new DataNotFoundException("未找到该计划的责任  planId：" + planId);
        }

        return toDtoList(liabilityList);
    }

    /**
     * 1. 版本+责任uuid取责任信息
     *
     */

    public List<LiabilityConfig> queryLiabilityByUuidAndVersion(List<String> liabilityUuidList, String version) {
        if (liabilityUuidList == null || liabilityUuidList.isEmpty()){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "uuid is null");
        }
        if (version == null || version.isBlank()) {
            return new ArrayList<>();
//            return queryNewestLiability(liabilityUuidList);
        }
        Criteria<Liability> criteria= Criteria.create();
        criteria.in("uuid", liabilityUuidList.toArray());
        criteria.eq("version", version);

        List<Liability> dbList =    liabilityMapper.findByCriteria(criteria);

        if (dbList == null || dbList.isEmpty()) {
            return new ArrayList<>();
        }

        return toDtoList(dbList);
    }


    private List<LiabilityConfig> toDtoList( List<Liability> dbList){
        List<LiabilityConfig> rs = new ArrayList<>();
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

    public List<LiabilityConfig> queryLiabilityByUuidAndVersion(String policyNo, List<String> liabilityUuidList, String version) {
        if (liabilityUuidList == null || liabilityUuidList.isEmpty()){
            throw new TpaBizException(BizErrorCode.PARAMETER_ERROR, "uuid is null");
        }
        if (version == null || version.isBlank()) {
            return new ArrayList<>();
//            return queryNewestLiability(liabilityUuidList);
        }
        Criteria<Liability> criteria= Criteria.create();
        criteria.in("uuid", liabilityUuidList.toArray());
        criteria.eq("version", version);
        if (StringUtils.isNotBlank(policyNo)) {
            criteria.eq("policyNo", policyNo);
        }

        List<Liability> dbList =    liabilityMapper.findByCriteria(criteria);

        if (dbList == null || dbList.isEmpty()) {
            return new ArrayList<>();
        }

        return toDtoList(dbList);
    }
}
