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
import com.bone.tpa.claim.application.response.GenericQueryResponse;
import com.bone.tpa.claim.application.response.MainInsurePerson;
import com.bone.tpa.claim.application.transfer.PersonFieldTransfer;
import com.bone.tpa.core.util.ExtraStoreUtil;
import com.bone.tpa.sdk.dao.ClaimStakeholderRepository;
import com.bone.tpa.claim.domain.service.ClaimService;
import com.bone.tpa.sdk.claim.enums.PersonTypeEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimHintMsg;
import com.bone.tpa.sdk.claim.model.ClaimStakeholder;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * 被保人信息通用处理逻辑
 *
 */
@Service
public class MainInsureStrategy extends DefaultStrategy {

    private final String TABLE = "ss_claim_stakeholder";

    @Autowired
    private ClaimStakeholderRepository stakeholderRepository;

    @Autowired
    private ClaimService claimService;

    @Override
    public BizModelEnum getBizModel() {
        return BizModelEnum.MAIN_INSURE_PERSON;
    }


    @Override
    public GenericQueryResponse queryOne(QueryOneRequest request) {
        ClaimStakeholder stakeholder = stakeholderRepository.findById(request.getId());

//        GenericQueryResponse response = new GenericQueryResponse(convertFromEntity(stakeholder));

        MainInsurePerson target = new MainInsurePerson();
        PersonFieldTransfer.transferToBizDto(stakeholder, target);
        target.setExtraStore(ExtraStoreUtil.jsonToMap(stakeholder.getExtraStore()));

        ClaimHintMsg claimHintMsg = querySyncHint(stakeholder.getRelatedId(), HintMsgType.MAIN_INSURE_PERSON, stakeholder.getId());
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
    public PageResult<MainInsurePerson> queryList(String modelName, List<QueryParam> paramList, List<SortingField> sortingFields, String bizIdentityCode, int offset, int pageSize) {
        paramList.add(new QueryParam("personType", PersonTypeEnum.MAIN_INSURE.getCode()));

        PageResult<ClaimStakeholder> result = stakeholderRepository.queryByCondition(paramList, sortingFields, offset, pageSize, TABLE, bizIdentityCode);

        List<ClaimStakeholder> stakeholderList = result.getData();
        List<MainInsurePerson> mainInsurePersonList = new ArrayList<>();
        for (ClaimStakeholder stakeholder : stakeholderList) {
            MainInsurePerson target = new MainInsurePerson();
            PersonFieldTransfer.transferToBizDto(stakeholder, target);
            target.setExtraStore(ExtraStoreUtil.jsonToMap(stakeholder.getExtraStore()));

            ClaimHintMsg claimHintMsg = querySyncHint(stakeholder.getRelatedId(), HintMsgType.MAIN_INSURE_PERSON, stakeholder.getId());
            if (claimHintMsg != null) {
                target.setSyncHintMsg(JsonUtil.fromJson(claimHintMsg.getData(), new TypeReference<Map<String, String>>(){}));
            }
            target.setId(stakeholder.getId());
            target.setTenantId(stakeholder.getTenantId());
            target.setBizIdentityCode(stakeholder.getBizIdentityCode());
            target.getExtraProperties().putAll(stakeholder.getExtraProperties());

            mainInsurePersonList.add(target);
        }

        return new PageResult<>(mainInsurePersonList, result.getCurrPage(), result.getPageSize(), result.getTotalCount());
    }

    @Override
    public void update(String modelName, Object data) {
        ClaimStakeholder stakeholder = new ClaimStakeholder();// convertToEntity(mapper.convertValue(data, MainInsurePerson.class));

        MainInsurePerson mainInsurePerson = mapper.convertValue(data, MainInsurePerson.class);

        PersonFieldTransfer.transferToEntity(stakeholder, mainInsurePerson);
        stakeholder.setPersonType(PersonTypeEnum.MAIN_INSURE.getCode());
        stakeholder.setId(mainInsurePerson.getId());
        if( mainInsurePerson.getExtraStore() == null){
            stakeholder.setExtraStore("{}");
        }else{
            stakeholder.setExtraStore(JSONObject.toJSONString(mainInsurePerson.getExtraStore()));
        }

        stakeholder.getExtraProperties().putAll(mainInsurePerson.getExtraProperties());
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

        stakeholderRepository.deleteByIds(request.getIdList());
    }


//    private MainInsurePerson convertFromEntity(ClaimStakeholder stakeholder) {
//        MainInsurePerson mainInsurePerson = new MainInsurePerson();
//        mainInsurePerson.setId(stakeholder.getId());
//        mainInsurePerson.setBizIdentityCode(stakeholder.getBizIdentityCode());
//        mainInsurePerson.setTenantId(stakeholder.getTenantId());
//        mainInsurePerson.setRelatedId(stakeholder.getRelatedId());
//        mainInsurePerson.setMainInsureName(stakeholder.getName());
//        mainInsurePerson.setMainInsureGender(stakeholder.getGender());
//        mainInsurePerson.setMainInsureBirthday(stakeholder.getBirthday());
//        mainInsurePerson.setMainInsureIdentityType(stakeholder.getIdentityType());
//        mainInsurePerson.setMainInsureIdentityNo(stakeholder.getIdentityNo());
//        mainInsurePerson.setMainInsureIdentityDatePeriod(stakeholder.getIdentityDatePeriod());
//        mainInsurePerson.setMainInsureOccupation(stakeholder.getOccupation());
//        mainInsurePerson.setMainInsureNationality(stakeholder.getNationality());
//        mainInsurePerson.setMainInsurePhone(stakeholder.getPhone());
//        mainInsurePerson.setMainInsureContactAddress(stakeholder.getContactAddress());
//        mainInsurePerson.setMainInsureRelationToOutInsure(stakeholder.getRelationToOutInsure());
//        mainInsurePerson.getExtraProperties().putAll(BizFieldUtil.fetchExtraBizField(stakeholder.getExtraProperties(), BizModelEnum.MAIN_INSURE_PERSON));
//
//        ClaimHintMsg claimHintMsg = querySyncHint(stakeholder.getRelatedId(), HintMsgType.MAIN_INSURE_PERSON, stakeholder.getId());
//        if (claimHintMsg != null) {
//            mainInsurePerson.setSyncHintMsg(JsonUtil.fromJson(claimHintMsg.getData(), new TypeReference<Map<String, String>>(){}));
//        }
//
//        return mainInsurePerson;
//    }

//    private ClaimStakeholder convertToEntity(MainInsurePerson mainInsurePerson) {
//        ClaimStakeholder stakeholder = new ClaimStakeholder();
//
//        stakeholder.setId(mainInsurePerson.getId());
//        stakeholder.setTenantId(mainInsurePerson.getTenantId());
//        stakeholder.setBizIdentityCode(mainInsurePerson.getBizIdentityCode());
//        stakeholder.setRelatedId(mainInsurePerson.getRelatedId());
//        stakeholder.setPersonType(PersonTypeEnum.MAIN_INSURE.getCode());
//        stakeholder.setName(mainInsurePerson.getMainInsureName());
//        stakeholder.setGender(mainInsurePerson.getMainInsureGender());
//        stakeholder.setBirthday(mainInsurePerson.getMainInsureBirthday());
//        stakeholder.setIdentityType(mainInsurePerson.getMainInsureIdentityType());
//        stakeholder.setIdentityNo(mainInsurePerson.getMainInsureIdentityNo());
//        stakeholder.setIdentityDatePeriod(mainInsurePerson.getMainInsureIdentityDatePeriod());
//        stakeholder.setOccupation(mainInsurePerson.getMainInsureOccupation());
//        stakeholder.setNationality(mainInsurePerson.getMainInsureNationality());
//        stakeholder.setPhone(mainInsurePerson.getMainInsurePhone());
//        stakeholder.setContactAddress(mainInsurePerson.getMainInsureContactAddress());
//        stakeholder.setRelationToOutInsure(mainInsurePerson.getMainInsureRelationToOutInsure());
//        stakeholder.getExtraProperties().putAll(mainInsurePerson.getExtraProperties());
//
//        return stakeholder;
//    }
}
