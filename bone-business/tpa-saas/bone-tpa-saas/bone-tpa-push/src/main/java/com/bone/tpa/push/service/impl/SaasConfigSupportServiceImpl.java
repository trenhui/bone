package com.bone.tpa.push.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.sdk.adjustment.model.LiabilityMapping;
import com.bone.tpa.sdk.dao.impl.LiabilityMappingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component("saasConfigSupportServiceImpl")
public class SaasConfigSupportServiceImpl extends TpaConfigSupportServiceImpl {

    @Autowired
    private LiabilityMappingRepository liabilityMappingRepository;

    @Override
    public Object getDutyConfig(String policyNo) {
        List<LiabilityMapping> emptyList = new ArrayList<>();
        Criteria<LiabilityMapping> criteria = new Criteria<>();
        criteria.eq(LiabilityMapping::getPolicyNo, policyNo);
        List<LiabilityMapping> liabilityMappings = liabilityMappingRepository.findByCriteria(criteria);
        if (CollectionUtil.isEmpty(liabilityMappings)) {
            return emptyList;
        }
        return liabilityMappings;
    }
}
