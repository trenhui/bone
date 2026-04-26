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
import com.bone.tpa.claim.application.response.OutInsurePerson;
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
 * 出险人信息通用处理逻辑
 *
 */
@Service
public class OutInsureStrategy extends DefaultStrategy {

    private final String TABLE = "ss_claim_stakeholder";

    @Autowired
    private ClaimStakeholderRepository stakeholderRepository;

    @Autowired
    private ClaimService claimService;

    @Override
    public BizModelEnum getBizModel() {
        return BizModelEnum.OUT_INSURE_PERSON;
    }


    @Override
    public GenericQueryResponse queryOne(QueryOneRequest request) {
        ClaimStakeholder stakeholder = stakeholderRepository.findById(request.getId());

//        GenericQueryResponse response = new GenericQueryResponse(convertFromEntity(stakeholder));

        OutInsurePerson target = new OutInsurePerson();
        PersonFieldTransfer.transferToBizDto(stakeholder, target);
        target.setExtraStore(ExtraStoreUtil.jsonToMap(stakeholder.getExtraStore()));

        ClaimHintMsg claimHintMsg = querySyncHint(stakeholder.getRelatedId(), HintMsgType.OUT_INSURE_PERSON, stakeholder.getId());
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
    public PageResult<OutInsurePerson> queryList(String modelName, List<QueryParam> paramList, List<SortingField> sortingFields, String bizIdentityCode, int offset, int pageSize) {
        paramList.add(new QueryParam("personType", PersonTypeEnum.OUT_INSURE.getCode()));

        PageResult<ClaimStakeholder> result = stakeholderRepository.queryByCondition(paramList, sortingFields, offset, pageSize, TABLE, bizIdentityCode);

        List<ClaimStakeholder> stakeholderList = result.getData();
        List<OutInsurePerson> outInsurePersonList = new ArrayList<>();
        for (ClaimStakeholder stakeholder : stakeholderList) {
            OutInsurePerson target = new OutInsurePerson();
            PersonFieldTransfer.transferToBizDto(stakeholder, target);
            target.setExtraStore(ExtraStoreUtil.jsonToMap(stakeholder.getExtraStore()));

            ClaimHintMsg claimHintMsg = querySyncHint(stakeholder.getRelatedId(), HintMsgType.OUT_INSURE_PERSON, stakeholder.getId());
            if (claimHintMsg != null) {
                target.setSyncHintMsg(JsonUtil.fromJson(claimHintMsg.getData(), new TypeReference<Map<String, String>>(){}));
            }
            target.setId(stakeholder.getId());
            target.setTenantId(stakeholder.getTenantId());
            target.setBizIdentityCode(stakeholder.getBizIdentityCode());
            target.getExtraProperties().putAll(stakeholder.getExtraProperties());

            outInsurePersonList.add(target);
        }

        return new PageResult<>(outInsurePersonList, result.getCurrPage(), result.getPageSize(), result.getTotalCount());
    }

    @Override
    public void update(String modelName, Object data) {
        ClaimStakeholder stakeholder = new ClaimStakeholder();// convertToEntity(mapper.convertValue(data, OutInsurePerson.class));

        OutInsurePerson outInsurePerson = mapper.convertValue(data, OutInsurePerson.class);

        PersonFieldTransfer.transferToEntity(stakeholder, outInsurePerson);
        stakeholder.setPersonType(PersonTypeEnum.OUT_INSURE.getCode());
        stakeholder.setId(outInsurePerson.getId());
        if( outInsurePerson.getExtraStore() == null){
            stakeholder.setExtraStore("{}");
        }else{
            stakeholder.setExtraStore(JSONObject.toJSONString(outInsurePerson.getExtraStore()));
        }

        stakeholder.getExtraProperties().putAll(outInsurePerson.getExtraProperties());
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


//    private OutInsurePerson convertFromEntity(ClaimStakeholder stakeholder) {
//        OutInsurePerson outInsurePerson = new OutInsurePerson();
//        outInsurePerson.setId(stakeholder.getId());
//        outInsurePerson.setBizIdentityCode(stakeholder.getBizIdentityCode());
//        outInsurePerson.setTenantId(stakeholder.getTenantId());
//        outInsurePerson.setRelatedId(stakeholder.getRelatedId());
//        outInsurePerson.setOutInsureName(stakeholder.getName());
//        outInsurePerson.setOutInsureGender(stakeholder.getGender());
//        outInsurePerson.setOutInsureBirthday(stakeholder.getBirthday());
//        outInsurePerson.setOutInsureIdentityType(stakeholder.getIdentityType());
//        outInsurePerson.setOutInsureIdentityNo(stakeholder.getIdentityNo());
//        outInsurePerson.setOutInsureIdentityDatePeriod(stakeholder.getIdentityDatePeriod());
//        outInsurePerson.setOutInsureOccupation(stakeholder.getOccupation());
//        outInsurePerson.setOutInsureNationality(stakeholder.getNationality());
//        outInsurePerson.setOutInsurePhone(stakeholder.getPhone());
//        outInsurePerson.setOutInsureContactAddress(stakeholder.getContactAddress());
//        outInsurePerson.getExtraProperties().putAll(BizFieldUtil.fetchExtraBizField(stakeholder.getExtraProperties(), BizModelEnum.OUT_INSURE_PERSON));
//
//        ClaimHintMsg claimHintMsg = querySyncHint(stakeholder.getRelatedId(), HintMsgType.OUT_INSURE_PERSON, stakeholder.getId());
//        if (claimHintMsg != null) {
//            outInsurePerson.setSyncHintMsg(JsonUtil.fromJson(claimHintMsg.getData(), new TypeReference<Map<String, String>>(){}));
//        }
//
//        return outInsurePerson;
//    }

//    private ClaimStakeholder convertToEntity(OutInsurePerson outInsurePerson) {
//        ClaimStakeholder stakeholder = new ClaimStakeholder();
//
//        stakeholder.setId(outInsurePerson.getId());
//        stakeholder.setTenantId(outInsurePerson.getTenantId());
//        stakeholder.setBizIdentityCode(outInsurePerson.getBizIdentityCode());
//        stakeholder.setRelatedId(outInsurePerson.getRelatedId());
//        stakeholder.setPersonType(PersonTypeEnum.OUT_INSURE.getCode());
//        stakeholder.setName(outInsurePerson.getOutInsureName());
//        stakeholder.setGender(outInsurePerson.getOutInsureGender());
//        stakeholder.setBirthday(outInsurePerson.getOutInsureBirthday());
//        stakeholder.setIdentityType(outInsurePerson.getOutInsureIdentityType());
//        stakeholder.setIdentityNo(outInsurePerson.getOutInsureIdentityNo());
//        stakeholder.setIdentityDatePeriod(outInsurePerson.getOutInsureIdentityDatePeriod());
//        stakeholder.setOccupation(outInsurePerson.getOutInsureOccupation());
//        stakeholder.setNationality(outInsurePerson.getOutInsureNationality());
//        stakeholder.setPhone(outInsurePerson.getOutInsurePhone());
//        stakeholder.setContactAddress(outInsurePerson.getOutInsureContactAddress());
//        stakeholder.getExtraProperties().putAll(outInsurePerson.getExtraProperties());
//
//        return stakeholder;
//    }
}
