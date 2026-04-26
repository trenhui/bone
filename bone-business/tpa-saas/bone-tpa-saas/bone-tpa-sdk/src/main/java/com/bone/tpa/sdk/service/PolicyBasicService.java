package com.bone.tpa.sdk.service;

import com.bone.core.result.PageResult;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.sdk.adjustment.model.Policy;
import com.bone.tpa.sdk.dao.impl.PolicyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 保单基础服务
 */
@Service
@Slf4j
public class PolicyBasicService {
    @Autowired
    private PolicyRepository policyMapper;



    public Policy getPolicyByPolicyNo(String policyNo) {
        Criteria<Policy> criteria = Criteria.create();
        criteria.eq("policyNo", policyNo);
        criteria.orderByDesc("id");

        PageResult<Policy> dbList =  policyMapper.pageByCriteria(criteria);
        return dbList.getList().get(0);
    }

    public List<Policy> getPolicyByPolicyNo(List<String> policyNoList) {
        Criteria<Policy> criteria = Criteria.create();
        criteria.in("policyNo", policyNoList.toArray());
        criteria.orderByDesc("id");

        PageResult<Policy> dbList =  policyMapper.pageByCriteria(criteria);
        return dbList.getList();
    }

    public List<Policy> getPolicyByParentPolicyNo(List<String> parentPolicyNoList) {
        Criteria<Policy> criteria = Criteria.create();
        criteria.in("parentPolicyNo", parentPolicyNoList.toArray());
        criteria.orderByDesc("id");

        PageResult<Policy> dbList =  policyMapper.pageByCriteria(criteria);
        return dbList.getList();
    }


}
