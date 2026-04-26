package com.bone.tpa.claim.domain.service;

import com.alibaba.fastjson.JSONObject;
import com.bone.core.exception.ServiceException;
import com.bone.core.util.JsonUtil;
import com.bone.metadata.sdk.MetadataFetchEngine;
import com.bone.metadata.sdk.SdkPropertyConfig;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.dto.MetaFieldDTO;
import com.bone.core.util.PkListUtil;
import com.bone.tpa.api.enums.HintMsgType;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.claim.application.response.ClaimDetailObject;
import com.bone.tpa.claim.application.response.MainInsurePerson;
import com.bone.tpa.claim.application.response.OutInsurePerson;
import com.bone.tpa.core.util.ExtraStoreUtil;
import com.bone.tpa.sdk.dao.ClaimHintMsgRespository;
import com.bone.tpa.sdk.dao.ClaimStakeholderRepository;
import com.bone.tpa.claim.util.BizFieldUtil;
import com.bone.tpa.sdk.claim.enums.PersonTypeEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimHintMsg;
import com.bone.tpa.sdk.claim.model.ClaimStakeholder;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;


/**
 * ss_claim_stakeholder Service 接口
 *
 * @author 0
 */
@Service
@Slf4j
public class ClaimStakeholderService {
    @Autowired
    private ClaimStakeholderRepository claimStakeholderRepository;

    @Autowired
    private ClaimHintMsgRespository hintMsgRespository;

    @Autowired
    private MetadataFetchEngine metadataFetchEngine;

    @Autowired
    private SdkPropertyConfig sdkPropertyConfig;


    /**
     * 查询特定赔案关联的相关人列表
     * 可填相关人类型
     *
     * @param claimId           赔案表id
     * @param bizIdentityCode   业务主体
     * @param tenantId          租户id
     * @param personType        相关人类型
     * @return
     */
    public List<ClaimStakeholder> getByClaimId(Long claimId, String bizIdentityCode, Long tenantId, List<String> personType) {
        Criteria<ClaimStakeholder> criteria = new Criteria<>();

        criteria.eq(ClaimStakeholder::getRelatedId, claimId)
                .eq(ClaimStakeholder::getTenantId, tenantId)
                .eq(ClaimStakeholder::getBizIdentityCode, bizIdentityCode);
        if (personType != null && !personType.isEmpty()) {
            criteria.in(ClaimStakeholder::getPersonType, personType);
        }

        return claimStakeholderRepository.findByCriteria(criteria);
    }

    /**
     * 查询特定赔案关联的相关人列表
     * 可填相关人类型
     *
     * @param claimIdList           赔案表id
     * @param personType        相关人类型
     * @return
     */
    public List<ClaimStakeholder> getByClaimIdList(List<Long> claimIdList, List<String> personType) {
        Criteria<ClaimStakeholder> criteria = new Criteria<>();

        criteria.in(ClaimStakeholder::getRelatedId, claimIdList);
        if (personType != null && !personType.isEmpty()) {
            criteria.in(ClaimStakeholder::getPersonType, personType);
        }

        return claimStakeholderRepository.findByCriteria(criteria);
    }

    /**
     * 插入单个相关人
     *
     * @return
     */
    public Long newStakeHolder(ClaimStakeholder stakeholder) {
        return claimStakeholderRepository.insert(stakeholder);
    }


    /**
     * 处理相关人信息
     *
     * @param claimDetailObject
     * @param stakeholderList
     */
    public void processClaimStakeHolder(ClaimDetailObject claimDetailObject, List<ClaimStakeholder> stakeholderList, Claim claim) {
        //相关人信息。遍历全部相关人
        //获取全部的专属字段
        List<MetaFieldDTO> bizFieldList = metadataFetchEngine.getAllBizIdentityField(sdkPropertyConfig.getAppcode(),
                BizModelEnum.OUT_INSURE_PERSON.getTableName(), claimDetailObject.getBizIdentityCode());

        //处理成 模型名, 字段名 的形式
        Map<String, List<String>> bizFieldMap;
        if (bizFieldList != null && !bizFieldList.isEmpty()) {
            bizFieldMap = bizFieldList.stream()
                    .collect(Collectors.groupingBy(
                            dto -> Optional.ofNullable(dto.getFieldModelCode()).orElse("DEFAULT_KEY"),
                            Collectors.mapping(MetaFieldDTO::getFieldName, Collectors.toList())
                    ));
        } else {
            bizFieldMap = new HashMap<>();
        }

        Optional.ofNullable(stakeholderList).ifPresent(list -> list.forEach(stakeholder -> {
            Optional<PersonTypeEnum> personType = PersonTypeEnum.getByCode(stakeholder.getPersonType());
            personType.ifPresent(type -> {
                switch (type) {
                    case OUT_INSURE:
                        OutInsurePerson outInsurePerson = new OutInsurePerson();
                        outInsurePerson.setId(stakeholder.getId());
                        outInsurePerson.setCreateTime(stakeholder.getCreateTime());
                        outInsurePerson.setUpdateTime(stakeholder.getUpdateTime());
                        outInsurePerson.setOutInsureName(stakeholder.getName());
                        outInsurePerson.setOutInsureGender(stakeholder.getGender());
                        outInsurePerson.setOutInsureBirthday(stakeholder.getBirthday());
                        outInsurePerson.setOutInsureIdentityType(stakeholder.getIdentityType());
                        outInsurePerson.setOutInsureIdentityTypeCn(stakeholder.getIdentityTypeCn());
                        outInsurePerson.setOutInsureIdentityNo(stakeholder.getIdentityNo());
                        outInsurePerson.setOutInsureIdentityDatePeriod(stakeholder.getIdentityDatePeriod());
                        outInsurePerson.setOutInsureOccupation(stakeholder.getOccupation());
                        outInsurePerson.setOutInsureOccupationCn(stakeholder.getOccupationCn());
                        outInsurePerson.setOutInsureNationality(stakeholder.getNationality());
                        outInsurePerson.setOutInsureNationalityCn(stakeholder.getNationalityCn());
                        outInsurePerson.setOutInsurePhone(stakeholder.getPhone());
                        outInsurePerson.setOutInsureContactAddress(stakeholder.getContactAddress());
                        outInsurePerson.setOutInsureContactRegion(stakeholder.getContactRegion());
                        outInsurePerson.setOutRelationToMainInsure(stakeholder.getRelationToMainInsure());
                        outInsurePerson.setOutRelationToMainInsureCn(stakeholder.getRelationToMainInsureCn());
                        outInsurePerson.getExtraProperties().putAll(BizFieldUtil.fetchExtraBizField(stakeholder.getExtraProperties(), bizFieldMap, BizModelEnum.OUT_INSURE_PERSON));
                        ClaimHintMsg outInsureHintMsg = querySyncHint(claimDetailObject.getId(), HintMsgType.OUT_INSURE_PERSON, stakeholder.getId());
                        if (outInsureHintMsg != null) {
                            outInsurePerson.setSyncHintMsg(JsonUtil.fromJson(outInsureHintMsg.getData(), new TypeReference<Map<String, String>>(){}));
                        }
                        String extraStore =  ExtraStoreUtil.fillExtraConfigForModel(outInsurePerson, stakeholder.getExtraStore());
                        outInsurePerson.setExtraStore(ExtraStoreUtil.jsonToMap(extraStore));
                        claimDetailObject.setOutInsurePerson(outInsurePerson);
                        break;
                    case MAIN_INSURE:
                        MainInsurePerson mainInsurePerson = new MainInsurePerson();
                        mainInsurePerson.setId(stakeholder.getId());
                        mainInsurePerson.setCreateTime(stakeholder.getCreateTime());
                        mainInsurePerson.setUpdateTime(stakeholder.getUpdateTime());
                        mainInsurePerson.setMainInsureName(stakeholder.getName());
                        mainInsurePerson.setMainInsureGender(stakeholder.getGender());
                        mainInsurePerson.setMainInsureBirthday(stakeholder.getBirthday());
                        mainInsurePerson.setMainInsureIdentityType(stakeholder.getIdentityType());
                        mainInsurePerson.setMainInsureIdentityTypeCn(stakeholder.getIdentityTypeCn());
                        mainInsurePerson.setMainInsureIdentityNo(stakeholder.getIdentityNo());
                        mainInsurePerson.setMainInsureIdentityDatePeriod(stakeholder.getIdentityDatePeriod());
                        mainInsurePerson.setMainInsureOccupation(stakeholder.getOccupation());
                        mainInsurePerson.setMainInsureOccupationCn(stakeholder.getOccupationCn());
                        mainInsurePerson.setMainInsureNationality(stakeholder.getNationality());
                        mainInsurePerson.setMainInsureNationalityCn(stakeholder.getNationalityCn());
                        mainInsurePerson.setMainInsurePhone(stakeholder.getPhone());
                        mainInsurePerson.setMainInsureContactAddress(stakeholder.getContactAddress());
                        mainInsurePerson.setMainInsureContactRegion(stakeholder.getContactRegion());
                        mainInsurePerson.setMainInsureRelationToOutInsure(stakeholder.getRelationToOutInsure());
                        mainInsurePerson.setMainInsureRelationToOutInsureCn(stakeholder.getRelationToOutInsureCn());
                        mainInsurePerson.getExtraProperties().putAll(BizFieldUtil.fetchExtraBizField(stakeholder.getExtraProperties(), bizFieldMap, BizModelEnum.MAIN_INSURE_PERSON));
                        ClaimHintMsg mainInsureHintMsg = querySyncHint(claimDetailObject.getId(), HintMsgType.MAIN_INSURE_PERSON, stakeholder.getId());
                        if (mainInsureHintMsg != null) {
                            mainInsurePerson.setSyncHintMsg(JsonUtil.fromJson(mainInsureHintMsg.getData(), new TypeReference<Map<String, String>>(){}));
                        }
                        String extraStoreMainPerson =  ExtraStoreUtil.fillExtraConfigForModel(mainInsurePerson, stakeholder.getExtraStore());
                        mainInsurePerson.setExtraStore(ExtraStoreUtil.jsonToMap(extraStoreMainPerson));
                        claimDetailObject.setMainInsurePerson(mainInsurePerson);
                        break;
                    default:
                        //log.warn("Person type: {}", stakeholder.getPersonType());
                }
            });
        }));

    }

    /**
     * 存储相关人信息
     *
     * @param claimDetailObject
     */
    public void saveStakeHolder(ClaimDetailObject claimDetailObject) {

        List<ClaimStakeholder> claimStakeholderList = new ArrayList<>();

        //依次处理赔案相关人信息
        //出险人
        if (claimDetailObject.getOutInsurePerson() != null) {
            OutInsurePerson outInsurePerson = claimDetailObject.getOutInsurePerson();

            ClaimStakeholder outInsure = new ClaimStakeholder();
            outInsure.setPersonType(PersonTypeEnum.OUT_INSURE.getCode());
            outInsure.setId(outInsurePerson.getId());
            outInsure.setRelatedId(claimDetailObject.getId());
            outInsure.setName(outInsurePerson.getOutInsureName());
            outInsure.setGender(outInsurePerson.getOutInsureGender());
            outInsure.setBirthday(outInsurePerson.getOutInsureBirthday());
            outInsure.setIdentityType(outInsurePerson.getOutInsureIdentityType());
            outInsure.setIdentityTypeCn(outInsurePerson.getOutInsureIdentityTypeCn());
            outInsure.setIdentityNo(outInsurePerson.getOutInsureIdentityNo());
            outInsure.setIdentityDatePeriod(outInsurePerson.getOutInsureIdentityDatePeriod());
            outInsure.setOccupation(outInsurePerson.getOutInsureOccupation());
            outInsure.setOccupationCn(outInsurePerson.getOutInsureOccupationCn());
            outInsure.setNationality(outInsurePerson.getOutInsureNationality());
            outInsure.setNationalityCn(outInsurePerson.getOutInsureNationalityCn());
            outInsure.setPhone(outInsurePerson.getOutInsurePhone());
            outInsure.setContactAddress(outInsurePerson.getOutInsureContactAddress());
            outInsure.setContactRegion(outInsurePerson.getOutInsureContactRegion());
            outInsure.setRelationToMainInsure(outInsurePerson.getOutRelationToMainInsure());
            outInsure.setRelationToMainInsureCn(outInsurePerson.getOutRelationToMainInsureCn());
            outInsure.getExtraProperties().putAll(outInsurePerson.getExtraProperties());
            //这两个字段取外层
            outInsure.setBizIdentityCode(claimDetailObject.getBizIdentityCode());
            outInsure.setTenantId(claimDetailObject.getTenantId());
            String extraStore = "{}";

            Map<String,Object>   extraMap =   outInsurePerson.getExtraStore();
            if( extraMap != null){
                extraStore = JSONObject.toJSONString(extraMap);
            }
           if(outInsure.getId() != null){
                ClaimStakeholder exist =   claimStakeholderRepository.findById(outInsure.getId());
               extraStore =   ExtraStoreUtil.mergeExtraStore(extraStore,exist.getExtraStore());

            }
            outInsure.setExtraStore(extraStore);

            claimStakeholderList.add(outInsure);
        }

        //被保人
        if (claimDetailObject.getMainInsurePerson() != null) {
            MainInsurePerson mainInsurePerson = claimDetailObject.getMainInsurePerson();

            ClaimStakeholder mainInsure = new ClaimStakeholder();
            mainInsure.setPersonType(PersonTypeEnum.MAIN_INSURE.getCode());
            mainInsure.setId(mainInsurePerson.getId());
            mainInsure.setRelatedId(claimDetailObject.getId());
            mainInsure.setName(mainInsurePerson.getMainInsureName());
            mainInsure.setGender(mainInsurePerson.getMainInsureGender());
            mainInsure.setBirthday(mainInsurePerson.getMainInsureBirthday());
            mainInsure.setIdentityType(mainInsurePerson.getMainInsureIdentityType());
            mainInsure.setIdentityTypeCn(mainInsurePerson.getMainInsureIdentityTypeCn());
            mainInsure.setIdentityNo(mainInsurePerson.getMainInsureIdentityNo());
            mainInsure.setIdentityDatePeriod(mainInsurePerson.getMainInsureIdentityDatePeriod());
            mainInsure.setOccupation(mainInsurePerson.getMainInsureOccupation());
            mainInsure.setOccupationCn(mainInsurePerson.getMainInsureOccupationCn());
            mainInsure.setNationality(mainInsurePerson.getMainInsureNationality());
            mainInsure.setNationalityCn(mainInsurePerson.getMainInsureNationalityCn());
            mainInsure.setPhone(mainInsurePerson.getMainInsurePhone());
            mainInsure.setContactAddress(mainInsurePerson.getMainInsureContactAddress());
            mainInsure.setContactRegion(mainInsurePerson.getMainInsureContactRegion());
            mainInsure.setRelationToOutInsure(mainInsurePerson.getMainInsureRelationToOutInsure());
            mainInsure.setRelationToOutInsureCn(mainInsurePerson.getMainInsureRelationToOutInsureCn());
            mainInsure.getExtraProperties().putAll(mainInsurePerson.getExtraProperties());

            //这两个字段取外层
            mainInsure.setBizIdentityCode(claimDetailObject.getBizIdentityCode());
            mainInsure.setTenantId(claimDetailObject.getTenantId());
            String extraStore = "{}";

            Map<String,Object>   extraMap =   mainInsurePerson.getExtraStore();
            if( extraMap != null){
                extraStore = JSONObject.toJSONString(extraMap);
            }


            if(mainInsure.getId() != null){
                ClaimStakeholder exist =   claimStakeholderRepository.findById(mainInsure.getId());
                extraStore =   ExtraStoreUtil.mergeExtraStore(extraStore,exist.getExtraStore());

            }
            mainInsure.setExtraStore(extraStore);

            claimStakeholderList.add(mainInsure);
        }

        //批量插入和更新
        claimStakeholderRepository.saveBatch(claimStakeholderList);

    }


    public void insertBatch(List<ClaimStakeholder> claimStakeholderList) {
        claimStakeholderRepository.insertBatch(claimStakeholderList);
    }


    /**
     * 查询同步提示信息
     *
     * 不穿后两个参数将会查出全部类型的，传入的话查询特定类型
     */
    private ClaimHintMsg querySyncHint(Long claimId, HintMsgType type, Long objectId) {
        Criteria<ClaimHintMsg> criteria = new Criteria();
        criteria.eq(ClaimHintMsg::getClaimNumber, claimId);
        if (type != null) {
            criteria.eq(ClaimHintMsg::getRelationId, objectId);
            criteria.eq(ClaimHintMsg::getHintType, type.getCode());
        }

        ClaimHintMsg hintMsg = PkListUtil.first(hintMsgRespository.findByCriteria(criteria));

        return hintMsg;
    }

    /**
     * 根据赔案号获取出险人
     */
    public ClaimStakeholder getOutInsurePeople(Long claimNumber) {
        Criteria<ClaimStakeholder> outInsureCriteria = new Criteria<>();
        outInsureCriteria.eq(ClaimStakeholder::getRelatedId, claimNumber)
                .eq(ClaimStakeholder::getPersonType, PersonTypeEnum.OUT_INSURE.getCode());
        List<ClaimStakeholder> outInsurePeoples = claimStakeholderRepository.findByCriteria(outInsureCriteria);
        if (CollectionUtils.isEmpty(outInsurePeoples)) {
            throw new ServiceException(500, "出险人不存在,claimNumber:" + claimNumber);
        }
        return outInsurePeoples.get(0);
    }
}
