package com.bone.tpa.claim.engine.strategy;

import com.alibaba.fastjson.JSONObject;
import com.bone.core.result.PageResult;
import com.bone.core.result.QueryParam;
import com.bone.core.result.SortingField;
import com.bone.core.util.JsonUtil;
import com.bone.tpa.api.enums.HintMsgType;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.claim.application.request.DeleteRequest;
import com.bone.tpa.claim.application.request.QueryOneRequest;
import com.bone.tpa.claim.application.response.BenefitPerson;
import com.bone.tpa.claim.application.response.GenericQueryResponse;
import com.bone.tpa.claim.application.transfer.PersonFieldTransfer;
import com.bone.tpa.core.util.ExtraStoreUtil;
import com.bone.tpa.sdk.dao.ClaimStakeholderRepository;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.claim.domain.service.ClaimStakeholderService;
import com.bone.tpa.sdk.claim.enums.BizErrorCode;
import com.bone.tpa.sdk.claim.enums.PersonTypeEnum;
import com.bone.tpa.sdk.claim.exception.TpaBizException;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimHintMsg;
import com.bone.tpa.sdk.claim.model.ClaimStakeholder;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;


/**
 * 受益人信息通用处理逻辑
 *
 */
@Service
public class BenefitStrategy extends DefaultStrategy {

    private final String TABLE = "ss_claim_stakeholder";

    @Autowired
    private ClaimStakeholderRepository stakeholderRepository;

    @Autowired
    private ClaimService claimService;

    @Autowired
    private ClaimStakeholderService claimStakeholderService;


    @Override
    public BizModelEnum getBizModel() {
        return BizModelEnum.BENEFIT_PERSON;
    }


    @Override
    public GenericQueryResponse queryOne(QueryOneRequest request) {
        ClaimStakeholder stakeholder = stakeholderRepository.findById(request.getId());

        BenefitPerson target = new BenefitPerson();
        PersonFieldTransfer.transferToBizDto(stakeholder, target);
        ClaimHintMsg claimHintMsg = querySyncHint(stakeholder.getRelatedId(), HintMsgType.BENEFIT_PERSON, stakeholder.getId());
        if (claimHintMsg != null) {
            target.setSyncHintMsg(JsonUtil.fromJson(claimHintMsg.getData(), new TypeReference<Map<String, String>>(){}));
        }

        target.setExtraStore(ExtraStoreUtil.jsonToMap(stakeholder.getExtraStore()));
        target.setId(stakeholder.getId());
        target.setTenantId(stakeholder.getTenantId());
        target.setBizIdentityCode(stakeholder.getBizIdentityCode());
        target.getExtraProperties().putAll(stakeholder.getExtraProperties());

        GenericQueryResponse response = new GenericQueryResponse(target);

        response.setTenantId(String.valueOf(stakeholder.getTenantId()));
        response.setBizIdentityCode(stakeholder.getBizIdentityCode());

        return response;
    }

    @Override
    public PageResult<BenefitPerson> queryList(String modelName, List<QueryParam> paramList, List<SortingField> sortingFields, String bizIdentityCode, int offset, int pageSize) {
        paramList.add(new QueryParam("personType", PersonTypeEnum.BENEFIT.getCode()));

        PageResult<ClaimStakeholder> result = stakeholderRepository.queryByCondition(paramList, sortingFields, offset, pageSize, TABLE, bizIdentityCode);

        List<ClaimStakeholder> stakeholderList = result.getData();
        List<BenefitPerson> benefitPersonList = new ArrayList<>();
        for (ClaimStakeholder stakeholder : stakeholderList) {
            BenefitPerson target = new BenefitPerson();
            PersonFieldTransfer.transferToBizDto(stakeholder, target);
            target.setExtraStore(ExtraStoreUtil.jsonToMap(stakeholder.getExtraStore()));

            ClaimHintMsg claimHintMsg = querySyncHint(stakeholder.getRelatedId(), HintMsgType.BENEFIT_PERSON, stakeholder.getId());
            if (claimHintMsg != null) {
                target.setSyncHintMsg(JsonUtil.fromJson(claimHintMsg.getData(), new TypeReference<Map<String, String>>(){}));
            }
            target.setId(stakeholder.getId());
            target.setTenantId(stakeholder.getTenantId());
            target.setBizIdentityCode(stakeholder.getBizIdentityCode());
            target.getExtraProperties().putAll(stakeholder.getExtraProperties());

            benefitPersonList.add(target);
        }

        return new PageResult<>(benefitPersonList, result.getCurrPage(), result.getPageSize(), result.getTotalCount());
    }

    @Override
    public void update(String modelName, Object data) {
        ClaimStakeholder stakeholder = new ClaimStakeholder();// convertToEntity(mapper.convertValue(data, BenefitPerson.class));

        BenefitPerson benefitPerson = mapper.convertValue(data, BenefitPerson.class);

        if (benefitPerson == null) {
            return;
        }

        PersonFieldTransfer.transferToEntity(stakeholder, benefitPerson);
        stakeholder.setPersonType(PersonTypeEnum.BENEFIT.getCode());
        stakeholder.setId(benefitPerson.getId());
        if( benefitPerson.getExtraStore() == null){
            stakeholder.setExtraStore("{}");
        }else{
            stakeholder.setExtraStore(JSONObject.toJSONString(benefitPerson.getExtraStore()));
        }

        stakeholder.getExtraProperties().putAll(benefitPerson.getExtraProperties());

        Claim claim = claimService.getById(stakeholder.getRelatedId());
        ClaimStakeholder exist =null;
        if(stakeholder.getId()!= null){
            exist = stakeholderRepository.findById(stakeholder.getId());
        }
        if(exist!= null){
            //需要做一次merge，避免全量覆盖
            String newStoreExtra =    ExtraStoreUtil.mergeExtraStore(stakeholder.getExtraStore(),exist.getExtraStore());
            stakeholder.setExtraStore(newStoreExtra);
        }

        claimService.checkFinish(claim);
        claimService.checkUserId(claim);

        stakeholderRepository.save(stakeholder);
    }

    @Override
    public void delete(DeleteRequest request) {
        if (request.getIdList() == null || request.getIdList().isEmpty()) {
            return;
        }

        ClaimStakeholder stakeholder = stakeholderRepository.findById(request.getIdList().get(0));

        Claim claim = claimService.getById(stakeholder.getRelatedId());
        claimService.checkFinish(claim);
        claimService.checkUserId(claim);

        //这里要获取该赔案的全部受益人，然后检查是不是试图删除全部
        List<ClaimStakeholder> benefitList = claimStakeholderService.getByClaimId(claim.getId(), claim.getBizIdentityCode(),
                claim.getTenantId(), Collections.singletonList(PersonTypeEnum.BENEFIT.getCode()));

        List<Long> existIdList = benefitList.stream().map(ClaimStakeholder::getId).toList();

        if (new HashSet<>(request.getIdList()).containsAll(existIdList)) {
            throw new TpaBizException(BizErrorCode.NO_DELETE_ALL);
        }

        stakeholderRepository.deleteByIds(request.getIdList());
    }


//    private BenefitPerson convertFromEntity(ClaimStakeholder stakeholder) {
//        BenefitPerson benefitPerson = new BenefitPerson();
//        benefitPerson.setId(stakeholder.getId());
//        benefitPerson.setBizIdentityCode(stakeholder.getBizIdentityCode());
//        benefitPerson.setTenantId(stakeholder.getTenantId());
//        benefitPerson.setRelatedId(stakeholder.getRelatedId());
//        benefitPerson.setBenefitName(stakeholder.getName());
//        benefitPerson.setBenefitGender(stakeholder.getGender());
//        benefitPerson.setBenefitBirthday(stakeholder.getBirthday());
//        benefitPerson.setBenefitIdentityType(stakeholder.getIdentityType());
//        benefitPerson.setBenefitIdentityNo(stakeholder.getIdentityNo());
//        benefitPerson.setBenefitIdentityDatePeriod(stakeholder.getIdentityDatePeriod());
//        benefitPerson.setBenefitOccupation(stakeholder.getOccupation());
//        benefitPerson.setBenefitNationality(stakeholder.getNationality());
//        benefitPerson.setBenefitPhone(stakeholder.getPhone());
//        benefitPerson.setBenefitContactAddress(stakeholder.getContactAddress());
//        benefitPerson.setBenefitRelationToOutInsure(stakeholder.getRelationToOutInsure());
//        benefitPerson.setBenefitRelationToMainInsure(stakeholder.getRelationToMainInsure());
//        benefitPerson.setBenefitPercentage(stakeholder.getBenefitPercentage());
//        benefitPerson.getExtraProperties().putAll(BizFieldUtil.fetchExtraBizField(stakeholder.getExtraProperties(), BizModelEnum.BENEFIT_PERSON));
//
//        ClaimHintMsg claimHintMsg = querySyncHint(stakeholder.getRelatedId(), HintMsgType.BENEFIT_PERSON, stakeholder.getId());
//        if (claimHintMsg != null) {
//            benefitPerson.setSyncHintMsg(JsonUtil.fromJson(claimHintMsg.getData(), new TypeReference<Map<String, String>>(){}));
//        }
//
//        return benefitPerson;
//    }

//    private ClaimStakeholder convertToEntity(BenefitPerson benefitPerson) {
//        ClaimStakeholder stakeholder = new ClaimStakeholder();
//        stakeholder.setId(benefitPerson.getId());
//        stakeholder.setTenantId(benefitPerson.getTenantId());
//        stakeholder.setBizIdentityCode(benefitPerson.getBizIdentityCode());
//        stakeholder.setRelatedId(benefitPerson.getRelatedId());
//        stakeholder.setPersonType(PersonTypeEnum.BENEFIT.getCode());
//        stakeholder.setName(benefitPerson.getBenefitName());
//        stakeholder.setGender(benefitPerson.getBenefitGender());
//        stakeholder.setBirthday(benefitPerson.getBenefitBirthday());
//        stakeholder.setIdentityType(benefitPerson.getBenefitIdentityType());
//        stakeholder.setIdentityNo(benefitPerson.getBenefitIdentityNo());
//        stakeholder.setIdentityDatePeriod(benefitPerson.getBenefitIdentityDatePeriod());
//        stakeholder.setOccupation(benefitPerson.getBenefitOccupation());
//        stakeholder.setNationality(benefitPerson.getBenefitNationality());
//        stakeholder.setPhone(benefitPerson.getBenefitPhone());
//        stakeholder.setContactAddress(benefitPerson.getBenefitContactAddress());
//        stakeholder.setRelationToOutInsure(benefitPerson.getBenefitRelationToOutInsure());
//        stakeholder.setRelationToMainInsure(benefitPerson.getBenefitRelationToMainInsure());
//        stakeholder.setBenefitPercentage(benefitPerson.getBenefitPercentage());
//        stakeholder.getExtraProperties().putAll(benefitPerson.getExtraProperties());
//
//        return stakeholder;
//    }
}
