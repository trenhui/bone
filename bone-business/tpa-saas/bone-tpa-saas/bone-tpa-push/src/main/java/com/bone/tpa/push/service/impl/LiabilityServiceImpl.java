package com.bone.tpa.push.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.tpa.push.bean.BatchDataBean;
import com.bone.tpa.push.service.LiabilityService;
import com.bone.tpa.sdk.adjustment.exception.DataNotFoundException;
import com.bone.tpa.sdk.adjustment.model.Coverage;
import com.bone.tpa.sdk.adjustment.model.Plan;
import com.bone.tpa.sdk.adjustment.model.liability.LiabilityConfig;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.dao.impl.CoverageRepository;
import com.bone.tpa.sdk.dao.impl.LiabilityRepository;
import com.bone.tpa.sdk.dao.impl.PlanRepository;
import com.bone.tpa.sdk.service.LiabilityInfoBasicService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author feihaiming
 * @create 2025/10/22 14:00
 */
@Service("pushLiabilityServiceImpl")
public class LiabilityServiceImpl implements LiabilityService {

    @Autowired
    private LiabilityRepository liabilityRepository;

    @Autowired
    private CoverageRepository coverageRepository;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private LiabilityInfoBasicService liabilityInfoBasicService;

//    @Override
//    public Liability getLiability(String liabilityUuid) {
//        Criteria<Liability> criteria = Criteria.create();
//        Liability liability = liabilityRepository.findOneByCriteria(criteria.eq(Liability::getUuid, liabilityUuid));
//        if (Objects.isNull(liability)) {
//            throw new DataNotFoundException("找不到该责任" + liabilityUuid);
//        }
//        return liability;
//    }

    public Coverage getCoverage(Long planId, String planVersion) {
        Criteria<Coverage> criteria = Criteria.create();
        criteria.eq(Coverage::getPlanId, planId);
        criteria.eq(Coverage::getVersion, planVersion);

        List<Coverage> coverageList = coverageRepository.findByCriteria(criteria);

        if (coverageList == null || coverageList.isEmpty()) {
            throw new DataNotFoundException("未找到该计划的险种  planId：" + planId);
        }

        return coverageList.get(0);
    }

    public Plan getPlan(Long planId, String planVersion) {
        Criteria<Plan> criteria = new Criteria<>();
        criteria.eq(Plan::getId, planId);
        criteria.eq(Plan::getVersion, planVersion);

        //获取该计划的所有责任
        Plan plan = planRepository.findOneByCriteria(criteria);

        return plan;
    }

    @Override
    public BatchDataBean getLiabilityMap(List<ClaimInvoice> claimInvoices, String version) {
        BatchDataBean batchDataBean = new BatchDataBean();
        if (CollectionUtil.isEmpty(claimInvoices)) {
            return batchDataBean;
        }

        List<String> liabilityUuids = claimInvoices.stream().filter(claimInvoice -> StringUtils.isNotEmpty(claimInvoice.getRelateLiability())).map(t -> t.getRelateLiability().split(",")).flatMap(Arrays::stream)
                .distinct().collect(Collectors.toList());
        List<LiabilityConfig> liabilities = liabilityInfoBasicService.queryLiabilityByUuidAndVersion(liabilityUuids, version);
        if (CollectionUtil.isNotEmpty(liabilities)) {
            Map<String, LiabilityConfig> liabilityConfigMap = liabilities.stream().collect(Collectors.toMap(l -> l.getUuid(), Function.identity(), (k1, k2) -> k1));
            batchDataBean.setLiabilityMap(liabilityConfigMap);
            Criteria<Coverage> criteria1 = Criteria.create();
            List<Long> coverageIds = liabilities.stream().map(LiabilityConfig::getCoverageId).collect(Collectors.toList());
            List<Coverage> coverageList = coverageRepository.findByCriteria(criteria1.in(Coverage::getId, coverageIds).eq(Coverage::getVersion, version));
            if (CollectionUtil.isNotEmpty(coverageList)) {
                batchDataBean.setCoverageMap(coverageList.stream().collect(Collectors.toMap(Coverage::getId, Function.identity(), (k1, k2) -> k1)));
            }
        }
        return batchDataBean;
    }
}
