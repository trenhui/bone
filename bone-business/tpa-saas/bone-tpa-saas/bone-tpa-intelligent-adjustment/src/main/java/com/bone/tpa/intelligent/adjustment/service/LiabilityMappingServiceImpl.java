package com.bone.tpa.intelligent.adjustment.service;


import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.sdk.adjustment.model.LiabilityMapping;
import com.bone.tpa.sdk.dao.impl.LiabilityMappingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Service
public class LiabilityMappingServiceImpl implements LiabilityMappingService {

    @Autowired
    private LiabilityMappingRepository liabilityMappingMapper;

    @Override
    public List<LiabilityMapping> getByPolicyNo(String policyNo) {
        Criteria<LiabilityMapping> criteria = new Criteria<>();
        criteria.eq(LiabilityMapping::getPolicyNo, policyNo);
        return liabilityMappingMapper.findByCriteria(criteria);
    }

    @Override
    public List<LiabilityMapping> getByPlanUuid(String planUuid) {
        Criteria<LiabilityMapping> criteria = new Criteria<>();
        criteria.eq(LiabilityMapping::getPlanUuid, planUuid);
        return liabilityMappingMapper.findByCriteria(criteria);
    }

    @Override
    public LiabilityMapping getByLiabilityAndVisitType(String liabilityUuid, String visitType) {
        Criteria<LiabilityMapping> criteria = new Criteria<>();
        criteria.eq(LiabilityMapping::getLiabilityUuid, liabilityUuid);
        criteria.eq(LiabilityMapping::getInvoiceMedicalType, visitType);

        List<LiabilityMapping> mappingList = liabilityMappingMapper.findByCriteria(criteria);

        if (mappingList == null || mappingList.isEmpty()) {
            return null;
        } else {
            return mappingList.get(0);
        }
    }

    @Override
    public void batchAdd(List<LiabilityMapping> insertList) {
        liabilityMappingMapper.insertBatch(insertList);
    }

    @Override
    public void batchUpdateById(List<LiabilityMapping> updateList) {
        for (LiabilityMapping mapping : updateList) {
            liabilityMappingMapper.update(mapping);
        }
    }

    @Override
    public void updateById(LiabilityMapping mapping) {
        liabilityMappingMapper.update(mapping);
    }

    @Override
    public void batchDeleteById(List<Long> deleteList) {
        liabilityMappingMapper.deleteByIds(deleteList);
    }

    @Override
    public void deleteByPolicyNo(String policyNo) {
        List<LiabilityMapping> list = getByPolicyNo(policyNo);
        if (!CollectionUtils.isEmpty(list)) {
            List<Long> idList = list.stream().map(AbstractEntity::getId).toList();
            liabilityMappingMapper.deleteByIds(idList);
        }
    }
}
