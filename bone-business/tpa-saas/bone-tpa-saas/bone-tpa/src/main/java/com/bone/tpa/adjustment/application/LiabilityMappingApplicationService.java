package com.bone.tpa.adjustment.application;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.exception.ServiceException;
import com.bone.core.util.BizContextUtils;
import com.bone.tpa.intelligent.adjustment.dto.PlanDTO;
import com.bone.tpa.intelligent.adjustment.service.LiabilityMappingService;
import com.bone.tpa.intelligent.adjustment.service.LiabilityService;
import com.bone.tpa.intelligent.adjustment.service.PlanService;
import com.bone.tpa.sdk.adjustment.enums.PlanStatus;
import com.bone.tpa.sdk.adjustment.model.Liability;
import com.bone.tpa.sdk.adjustment.model.LiabilityMapping;
import com.bone.tpa.sdk.adjustment.request.LiabilityMappingReq;
import com.bone.tpa.sdk.adjustment.response.LiabilityMappingRes;
import com.bone.tpa.sdk.claim.enums.VisitTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LiabilityMappingApplicationService {

    @Autowired
    private PlanService planService;

    @Autowired
    private LiabilityService liabilityService;

    @Autowired
    private LiabilityMappingService liabilityMappingService;

    /**
     * 给已发布计划下的责任生成责任映射数据
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateLiabilityMapping(Long oldPlanId) {
        //根据发布前的计划id获取发布后的计划
        PlanDTO oldPlan = planService.findById(oldPlanId);
        if (oldPlan == null || !StringUtils.hasText(oldPlan.getUuid())) {
            log.info("找不到当前计划的uuid,计划id:{}", oldPlanId);
            return;
        }
        String planUuid = oldPlan.getUuid();
        PlanDTO planDTO = planService.queryByPlanUuid(planUuid);
        if (planDTO == null || !Objects.equals(planDTO.getStatus(), PlanStatus.ACTIVE.getCode())) {
            log.info("当前计划不存在或未发布,计划uuid:{},old计划id:{}", planUuid, oldPlanId);
            return;
        }
        
        

        Long planId = planDTO.getId();
        List<Liability> liabilityList = liabilityService.getByPlanId(planId);
        List<LiabilityMapping> mappingList = liabilityMappingService.getByPlanUuid(planUuid);
        List<Long> deleteList = new ArrayList<>();
        if (CollectionUtils.isEmpty(liabilityList)) {
            //无责任则删除映射数据
            deleteList.addAll(mappingList.stream().map(AbstractEntity::getId).toList());
            if (!CollectionUtils.isEmpty(deleteList)) {
                liabilityMappingService.batchDeleteById(deleteList);
            }
            return;
        }

        //找出不在责任范围内的映射数据并且删除
        Set<String> liabilityUuidSet = liabilityList.stream().map(Liability::getUuid).collect(Collectors.toSet());
        List<LiabilityMapping> list = new ArrayList<>();
        for (LiabilityMapping mapping : mappingList) {
            if (!liabilityUuidSet.contains(mapping.getLiabilityUuid())) {
                deleteList.add(mapping.getId());
            } else {
                list.add(mapping);
            }
        }

        //发票医疗类型有值的映射数据组
        List<LiabilityMapping> hasMedicalTypeGroup = list.stream().filter(i -> StringUtils.hasText(i.getInvoiceMedicalType())).toList();
        Map<String, List<LiabilityMapping>> hasMedicalTypeGroupMap1 = hasMedicalTypeGroup.stream().collect(Collectors.groupingBy(i -> i.getLiabilityUuid() + "_" + i.getInvoiceMedicalType()));
        Map<String, List<LiabilityMapping>> hasMedicalTypeGroupMap2 = hasMedicalTypeGroup.stream().collect(Collectors.groupingBy(LiabilityMapping::getLiabilityUuid));

        //发票医疗类型没有值的映射数据组
        List<LiabilityMapping> noMedicalTypeGroup = list.stream().filter(i -> !StringUtils.hasText(i.getInvoiceMedicalType())).toList();
        Map<String, List<LiabilityMapping>> noMedicalTypeGroupMap = noMedicalTypeGroup.stream().collect(Collectors.groupingBy(LiabilityMapping::getLiabilityUuid));

        Date now = new Date();
        String policyNo = planDTO.getPolicyNo();
        String planName = planDTO.getPlanName();
        String operateUser = BizContextUtils.getUser();
        List<LiabilityMapping> insertList = new ArrayList<>();
        List<LiabilityMapping> updateList = new ArrayList<>();
        for (Liability liability : liabilityList) {
            String liabilityUuid = liability.getUuid();
            String liabilityName = liability.getLiabilityName();
            List<String> medicalTypeList = getRestrictOutInsureList(liability.getRestrictOutInsure());
            log.info("责任uuid:{},责任名:{}, 医疗类型:{}", liabilityUuid, liabilityName, medicalTypeList);

            if (!CollectionUtils.isEmpty(medicalTypeList)) {
                if (!CollectionUtils.isEmpty(noMedicalTypeGroupMap.get(liabilityUuid))) {
                    deleteList.addAll(noMedicalTypeGroupMap.get(liabilityUuid).stream().map(AbstractEntity::getId).toList());
                }

                for (String medicalType : medicalTypeList) {
                    //对于每个责任,每个发票医疗类型需要生成一条数据,如果已存在就更新,不存在就新增
                    String key = liabilityUuid + "_" + medicalType;

                    if (hasMedicalTypeGroupMap1.containsKey(key)) {
                        List<LiabilityMapping> temList = hasMedicalTypeGroupMap1.get(key);
                        if (temList.size() >= 2) {
                            //如果一个发票医疗类型出现重复数据,需要删除重复数据
                            for (int i = 1; i < temList.size(); i++) {
                                deleteList.add(temList.get(i).getId());
                            }
                        }
                        LiabilityMapping oldMapping = temList.get(0);
                        LiabilityMapping update = new LiabilityMapping();
                        update.setId(oldMapping.getId());
                        setField(update, policyNo, planUuid, planName, liabilityUuid, liabilityName);
                        update.setInvoiceMedicalType(medicalType);
                        update.setUpdateUser(operateUser);
                        update.setUpdateTime(now);
                        updateList.add(update);
                    } else {
                        LiabilityMapping mapping = new LiabilityMapping();
                        setField(mapping, policyNo, planUuid, planName, liabilityUuid, liabilityName);
                        mapping.setInvoiceMedicalType(medicalType);
                        mapping.setCreateUser(operateUser);
                        mapping.setCreateTime(now);
                        insertList.add(mapping);
                    }
                }
            } else {
                if (!CollectionUtils.isEmpty(hasMedicalTypeGroupMap2.get(liabilityUuid))) {
                    deleteList.addAll(hasMedicalTypeGroupMap2.get(liabilityUuid).stream().map(AbstractEntity::getId).toList());
                }

                //对于每个责任,如果发票医疗类型缺失,也需要生成一条数据
                List<LiabilityMapping> temList = noMedicalTypeGroupMap.get(liabilityUuid);
                if (!CollectionUtils.isEmpty(temList)) {
                    LiabilityMapping oldMapping = temList.get(0);
                    LiabilityMapping update = new LiabilityMapping();
                    update.setId(oldMapping.getId());
                    setField(update, policyNo, planUuid, planName, liabilityUuid, liabilityName);
                    update.setUpdateUser(operateUser);
                    update.setUpdateTime(now);
                    updateList.add(update);

                    if (temList.size() >= 2) {
                        //如果一个发票医疗类型出现重复数据,需要删除重复数据
                        for (int i = 1; i < temList.size(); i++) {
                            deleteList.add(temList.get(i).getId());
                        }
                    }
                } else {
                    LiabilityMapping mapping = new LiabilityMapping();
                    setField(mapping, policyNo, planUuid, planName, liabilityUuid, liabilityName);
                    mapping.setCreateUser(operateUser);
                    mapping.setCreateTime(now);
                    insertList.add(mapping);
                }
            }
        }

        log.info("计划uuid:{},新增责任映射数量:{},更新责任映射数量:{},删除责任映射数量:{}", planUuid, insertList.size(), updateList.size(), deleteList.size());
        if (!CollectionUtils.isEmpty(insertList)) {
            liabilityMappingService.batchAdd(insertList);
        }
        if (!CollectionUtils.isEmpty(updateList)) {
            liabilityMappingService.batchUpdateById(updateList);
        }
        if (!CollectionUtils.isEmpty(deleteList)) {
            liabilityMappingService.batchDeleteById(deleteList);
        }
    }

    private void setField(LiabilityMapping mapping, String policyNo,
                          String planUuid, String planName, String liabilityUuid, String liabilityName) {
        mapping.setPolicyNo(policyNo);
        mapping.setPlanUuid(planUuid);
        mapping.setPlanName(planName);
        mapping.setLiabilityUuid(liabilityUuid);
        mapping.setLiabilityName(liabilityName);
    }

    /**
     * 解析责任对应的适用出险字段
     */
    private List<String> getRestrictOutInsureList(String medicalTypeListStr) {
        if (!StringUtils.hasText(medicalTypeListStr)) {
            return List.of();
        }

        try {
            JSONObject jsonObj = JSON.parseObject(medicalTypeListStr);
            JSONArray visitTypes = jsonObj.getJSONArray("visitType");
            return visitTypes.toJavaList(String.class);
        } catch (Exception e) {
            log.info("解析责任对应的适用出险字段发生异常", e);
            return List.of();
        }
    }

    public List<LiabilityMappingRes> getLiabilityMapping(String policyNo) {
        List<LiabilityMapping> list = liabilityMappingService.getByPolicyNo(policyNo);
        return list.stream().map(mapping -> {
            LiabilityMappingRes re = new LiabilityMappingRes();
            BeanUtils.copyProperties(mapping, re);
            re.setId(mapping.getId().toString());
            re.setInvoiceMedicalTypeCN(VisitTypeEnum.getValueByCode(mapping.getInvoiceMedicalType()));
            return re;
        }).toList();
    }

    public Boolean updateLiabilityMapping(LiabilityMappingReq param) {
        LiabilityMapping mapping = new LiabilityMapping();
        BeanUtils.copyProperties(param, mapping);
        mapping.setUpdateUser(BizContextUtils.getUser());
        mapping.setUpdateTime(new Date());
        liabilityMappingService.updateById(mapping);
        return true;
    }

    public Boolean batchUpdateLiabilityMapping(List<LiabilityMappingReq> param) {
        if (CollectionUtils.isEmpty(param)) {
            throw new ServiceException(500, "参数列表不能为空");
        }

        String operateUser = BizContextUtils.getUser();
        Date now = new Date();
        List<LiabilityMapping> list = param.stream().map(req -> {
            LiabilityMapping mapping = new LiabilityMapping();
            BeanUtils.copyProperties(req, mapping);
            mapping.setUpdateUser(operateUser);
            mapping.setUpdateTime(now);
            return mapping;
        }).toList();

        liabilityMappingService.batchUpdateById(list);
        return true;
    }
}
