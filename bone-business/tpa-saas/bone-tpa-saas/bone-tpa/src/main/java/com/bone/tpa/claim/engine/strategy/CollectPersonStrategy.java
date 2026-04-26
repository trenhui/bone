package com.bone.tpa.claim.engine.strategy;

import com.alibaba.fastjson.JSONObject;
import com.bone.core.result.PageResult;
import com.bone.core.result.QueryParam;
import com.bone.core.result.SortingField;
import com.bone.core.util.JsonUtil;
import com.bone.tpa.api.enums.CollectTypeEnum;
import com.bone.tpa.api.enums.HintMsgType;
import com.bone.tpa.claim.application.enums.BizModelEnum;
import com.bone.tpa.claim.application.request.DeleteRequest;
import com.bone.tpa.claim.application.request.QueryOneRequest;
import com.bone.tpa.claim.application.response.CollectPerson;
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
 * 领款人信息通用处理逻辑
 *
 */
@Service
public class CollectPersonStrategy extends DefaultStrategy {

    private final String TABLE = "ss_claim_stakeholder";

    @Autowired
    private ClaimStakeholderRepository stakeholderRepository;

    @Autowired
    private ClaimService claimService;

    @Autowired
    private ClaimStakeholderService claimStakeholderService;

    @Override
    public BizModelEnum getBizModel() {
        return BizModelEnum.COLLECT_PERSON;
    }


    @Override
    public GenericQueryResponse queryOne(QueryOneRequest request) {
        ClaimStakeholder stakeholder = stakeholderRepository.findById(request.getId());

//        GenericQueryResponse response = new GenericQueryResponse(convertFromEntity(stakeholder));
        CollectPerson target = new CollectPerson();
        PersonFieldTransfer.transferToBizDto(stakeholder, target);
        target.setExtraStore(ExtraStoreUtil.jsonToMap(stakeholder.getExtraStore()));

        ClaimHintMsg claimHintMsg = querySyncHint(stakeholder.getRelatedId(), HintMsgType.COLLECT_PERSON, stakeholder.getId());
        if (claimHintMsg != null) {
            target.setSyncHintMsg(JsonUtil.fromJson(claimHintMsg.getData(), new TypeReference<Map<String, String>>(){}));
        }
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
    public PageResult<CollectPerson> queryList(String modelName, List<QueryParam> paramList, List<SortingField> sortingFields, String bizIdentityCode, int offset, int pageSize) {
        paramList.add(new QueryParam("personType", PersonTypeEnum.COLLECT.getCode()));

        PageResult<ClaimStakeholder> result = stakeholderRepository.queryByCondition(paramList, sortingFields, offset, pageSize, TABLE, bizIdentityCode);

        List<ClaimStakeholder> stakeholderList = result.getData();
        List<CollectPerson> collectPersonList = new ArrayList<>();
        for (ClaimStakeholder stakeholder : stakeholderList) {
            CollectPerson target = new CollectPerson();
            PersonFieldTransfer.transferToBizDto(stakeholder, target);
            target.setExtraStore(ExtraStoreUtil.jsonToMap(stakeholder.getExtraStore()));

            ClaimHintMsg claimHintMsg = querySyncHint(stakeholder.getRelatedId(), HintMsgType.COLLECT_PERSON, stakeholder.getId());
            if (claimHintMsg != null) {
                target.setSyncHintMsg(JsonUtil.fromJson(claimHintMsg.getData(), new TypeReference<Map<String, String>>(){}));
            }
            target.setId(stakeholder.getId());
            target.setTenantId(stakeholder.getTenantId());
            target.setBizIdentityCode(stakeholder.getBizIdentityCode());
            target.getExtraProperties().putAll(stakeholder.getExtraProperties());

            collectPersonList.add(target);
        }

        return new PageResult<>(collectPersonList, result.getCurrPage(), result.getPageSize(), result.getTotalCount());
    }

    @Override
    public void update(String modelName, Object data) {
        ClaimStakeholder stakeholder = new ClaimStakeholder();// convertToEntity(mapper.convertValue(data, CollectPerson.class));

        CollectPerson collectPerson = mapper.convertValue(data, CollectPerson.class);

        PersonFieldTransfer.transferToEntity(stakeholder, collectPerson);
        stakeholder.setPersonType(PersonTypeEnum.COLLECT.getCode());
        stakeholder.setId(collectPerson.getId());
        stakeholder.getExtraProperties().putAll(collectPerson.getExtraProperties());
        if( collectPerson.getExtraStore() == null){
            stakeholder.setExtraStore("{}");
        }else{
            stakeholder.setExtraStore(JSONObject.toJSONString(collectPerson.getExtraStore()));
        }
        ClaimStakeholder exist =null;
        if(stakeholder.getId()!= null){
            exist = stakeholderRepository.findById(stakeholder.getId());
        }
        if(exist!= null){
            //需要做一次merge，避免全量覆盖
            String newStoreExtra =    ExtraStoreUtil.mergeExtraStore(stakeholder.getExtraStore(),exist.getExtraStore());
            stakeholder.setExtraStore(newStoreExtra);
        }

        Claim claim = claimService.getById(stakeholder.getRelatedId());
        claimService.checkFinish(claim);
        claimService.checkUserId(claim);
        stakeholder.setTransferMethodType(CollectTypeEnum.PERSON.getTransferMethod());

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

        //这里要获取该赔案的全部领款人，然后检查是不是试图删除全部
        List<ClaimStakeholder> collectList = claimStakeholderService.getByClaimId(claim.getId(), claim.getBizIdentityCode(),
                claim.getTenantId(), Collections.singletonList(PersonTypeEnum.COLLECT_BUSINESS.getCode()));

        List<Long> existIdList = collectList.stream().map(ClaimStakeholder::getId).toList();

        if (new HashSet<>(request.getIdList()).containsAll(existIdList)) {
            throw new TpaBizException(BizErrorCode.NO_DELETE_ALL);
        }

        stakeholderRepository.deleteByIds(request.getIdList());
    }


//    private CollectPerson convertFromEntity(ClaimStakeholder stakeholder) {
//        CollectPerson collectPerson = new CollectPerson();
//        collectPerson.setId(stakeholder.getId());
//        collectPerson.setBizIdentityCode(stakeholder.getBizIdentityCode());
//        collectPerson.setTenantId(stakeholder.getTenantId());
//        collectPerson.setRelatedId(stakeholder.getRelatedId());
//
//        collectPerson.setCollectBirthday(stakeholder.getBirthday());
//        collectPerson.setCollectGender(stakeholder.getGender());
//        collectPerson.setCollectName(stakeholder.getName());
//        collectPerson.setCollectIdentityType(stakeholder.getIdentityType());
//        collectPerson.setCollectIdentityNo(stakeholder.getIdentityNo());
//        collectPerson.setCollectIdentityDatePeriod(stakeholder.getIdentityDatePeriod());
//        collectPerson.setCollectOccupation(stakeholder.getOccupation());
//        collectPerson.setCollectNationality(stakeholder.getNationality());
//        collectPerson.setCollectPhone(stakeholder.getPhone());
//        collectPerson.setCollectContactAddress(stakeholder.getContactAddress());
//
//        collectPerson.setPersonAccountNo(stakeholder.getAccountNo());
//        collectPerson.setPersonBankCode(stakeholder.getBankCode());
//        collectPerson.setPersonBankAddress(stakeholder.getBankAddress());
//        collectPerson.setPersonBranchCode(stakeholder.getBranchCode());
//        collectPerson.setPersonTransferMethodType(stakeholder.getTransferMethodType());
//        collectPerson.setPersonPaymentMethodType(stakeholder.getPaymentMethodType());
//
//        collectPerson.setCollectRelationToBenefit(stakeholder.getRelationToBenefit());
//        collectPerson.setCollectRelationToMainInsure(stakeholder.getRelationToMainInsure());
//        collectPerson.setCollectRelationToOutInsure(stakeholder.getRelationToOutInsure());
//
//
//        collectPerson.getExtraProperties().putAll(BizFieldUtil.fetchExtraBizField(stakeholder.getExtraProperties(), BizModelEnum.COLLECT_PERSON));
//
//        ClaimHintMsg claimHintMsg = querySyncHint(stakeholder.getRelatedId(), HintMsgType.COLLECT_PERSON, stakeholder.getId());
//        if (claimHintMsg != null) {
//            collectPerson.setSyncHintMsg(JsonUtil.fromJson(claimHintMsg.getData(), new TypeReference<Map<String, String>>(){}));
//        }
//
//        return collectPerson;
//    }

//    private ClaimStakeholder convertToEntity(CollectPerson collectPerson) {
//        ClaimStakeholder stakeholder = new ClaimStakeholder();
//        stakeholder.setId(collectPerson.getId());
//        stakeholder.setTenantId(collectPerson.getTenantId());
//        stakeholder.setBizIdentityCode(collectPerson.getBizIdentityCode());
//        stakeholder.setRelatedId(collectPerson.getRelatedId());
//        stakeholder.setPersonType(PersonTypeEnum.COLLECT.getCode());
//
//        stakeholder.setBirthday(collectPerson.getCollectBirthday());
//        stakeholder.setGender(collectPerson.getCollectGender());
//        stakeholder.setName(collectPerson.getCollectName());
//        stakeholder.setIdentityType(collectPerson.getCollectIdentityNo());
//        stakeholder.setIdentityNo(collectPerson.getCollectIdentityNo());
//        stakeholder.setIdentityDatePeriod(collectPerson.getCollectIdentityDatePeriod());
//        stakeholder.setOccupation(collectPerson.getCollectOccupation());
//        stakeholder.setNationality(collectPerson.getCollectNationality());
//        stakeholder.setPhone(collectPerson.getCollectPhone());
//        stakeholder.setContactAddress(collectPerson.getCollectContactAddress());
//
//        stakeholder.setAccountNo(collectPerson.getPersonAccountNo());
//        stakeholder.setBankCode(collectPerson.getPersonBankCode());
//        stakeholder.setBankAddress(collectPerson.getPersonBankAddress());
//        stakeholder.setBranchCode(collectPerson.getPersonBranchCode());
//        stakeholder.setTransferMethodType(collectPerson.getPersonTransferMethodType());
//        stakeholder.setPaymentMethodType(collectPerson.getPersonPaymentMethodType());
//
//        stakeholder.setRelationToBenefit(collectPerson.getCollectRelationToBenefit());
//        stakeholder.setRelationToMainInsure(collectPerson.getCollectRelationToMainInsure());
//        stakeholder.setRelationToOutInsure(collectPerson.getCollectRelationToOutInsure());
//
//        return stakeholder;
//    }
}
