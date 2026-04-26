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
import com.bone.tpa.claim.application.response.CollectBusiness;
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
 * 领款单位信息通用处理逻辑
 *
 */
@Service
public class CollectBusinessStrategy extends DefaultStrategy {

    private final String TABLE = "ss_claim_stakeholder";

    @Autowired
    private ClaimStakeholderRepository stakeholderRepository;

    @Autowired
    private ClaimService claimService;

    @Autowired
    private ClaimStakeholderService claimStakeholderService;

    @Override
    public BizModelEnum getBizModel() {
        return BizModelEnum.COLLECT_BUSINESS;
    }


    @Override
    public GenericQueryResponse queryOne(QueryOneRequest request) {
        ClaimStakeholder stakeholder = stakeholderRepository.findById(request.getId());

//        GenericQueryResponse response = new GenericQueryResponse(convertFromEntity(stakeholder));

        CollectBusiness target = new CollectBusiness();
        PersonFieldTransfer.transferToBizDto(stakeholder, target);
        target.setExtraStore(ExtraStoreUtil.jsonToMap(stakeholder.getExtraStore()));

        ClaimHintMsg claimHintMsg = querySyncHint(stakeholder.getRelatedId(), HintMsgType.COLLECT_BUSINESS, stakeholder.getId());
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
    public PageResult<CollectBusiness> queryList(String modelName, List<QueryParam> paramList, List<SortingField> sortingFields, String bizIdentityCode, int offset, int pageSize) {
        paramList.add(new QueryParam("personType", PersonTypeEnum.COLLECT_BUSINESS.getCode()));

        PageResult<ClaimStakeholder> result = stakeholderRepository.queryByCondition(paramList, sortingFields, offset, pageSize, TABLE, bizIdentityCode);

        List<ClaimStakeholder> stakeholderList = result.getData();
        List<CollectBusiness> collectBusinessList = new ArrayList<>();
        for (ClaimStakeholder stakeholder : stakeholderList) {
            CollectBusiness target = new CollectBusiness();
            PersonFieldTransfer.transferToBizDto(stakeholder, target);
            target.setExtraStore(ExtraStoreUtil.jsonToMap(stakeholder.getExtraStore()));

            ClaimHintMsg claimHintMsg = querySyncHint(stakeholder.getRelatedId(), HintMsgType.COLLECT_BUSINESS, stakeholder.getId());
            if (claimHintMsg != null) {
                target.setSyncHintMsg(JsonUtil.fromJson(claimHintMsg.getData(), new TypeReference<Map<String, String>>(){}));
            }
            target.setId(stakeholder.getId());
            target.setTenantId(stakeholder.getTenantId());
            target.setBizIdentityCode(stakeholder.getBizIdentityCode());
            target.getExtraProperties().putAll(stakeholder.getExtraProperties());

            collectBusinessList.add(target);
        }

        return new PageResult<>(collectBusinessList, result.getCurrPage(), result.getPageSize(), result.getTotalCount());
    }

    @Override
    public void update(String modelName, Object data) {
        ClaimStakeholder stakeholder = new ClaimStakeholder();// convertToEntity(mapper.convertValue(data, CollectBusiness.class));

        CollectBusiness collectBusiness = mapper.convertValue(data, CollectBusiness.class);

        PersonFieldTransfer.transferToEntity(stakeholder, collectBusiness);
        stakeholder.setPersonType(PersonTypeEnum.COLLECT_BUSINESS.getCode());
        stakeholder.setId(collectBusiness.getId());
        stakeholder.getExtraProperties().putAll(collectBusiness.getExtraProperties());
        if( collectBusiness.getExtraStore() == null){
            stakeholder.setExtraStore("{}");
        }else{
            stakeholder.setExtraStore(JSONObject.toJSONString(collectBusiness.getExtraStore()));
        }
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
        stakeholder.setTransferMethodType(CollectTypeEnum.COMPANY.getTransferMethod());

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


//    private CollectBusiness convertFromEntity(ClaimStakeholder stakeholder) {
//        CollectBusiness collectBusiness = new CollectBusiness();
//        collectBusiness.setId(stakeholder.getId());
//        collectBusiness.setBizIdentityCode(stakeholder.getBizIdentityCode());
//        collectBusiness.setTenantId(stakeholder.getTenantId());
//        collectBusiness.setRelatedId(stakeholder.getRelatedId());
//
//        collectBusiness.setLegalName(stakeholder.getName());
//        collectBusiness.setLegalIdentityType(stakeholder.getIdentityType());
//        collectBusiness.setLegalIdentityNo(stakeholder.getIdentityNo());
//        collectBusiness.setLegalIdentityDatePeriod(stakeholder.getIdentityDatePeriod());
//        collectBusiness.setLegalPhone(stakeholder.getPhone());
//        collectBusiness.setLegalContactAddress(stakeholder.getContactAddress());
//
//        collectBusiness.setBusinessName(stakeholder.getBusinessName());
//        collectBusiness.setBusinessIdentityDisc(stakeholder.getBusinessIdentityDisc());
//        collectBusiness.setBusinessIdentityType(stakeholder.getBusinessIdentityType());
//        collectBusiness.setBusinessIdentityNo(stakeholder.getBusinessIdentityNo());
//        collectBusiness.setBusinessIdentityDatePeriod(stakeholder.getBusinessIdentityDatePeriod());
//        collectBusiness.setBusinessPlace(stakeholder.getBusinessPlace());
//        collectBusiness.setBusinessRange(stakeholder.getBusinessRange());
//
//        collectBusiness.setBusinessAccountNo(stakeholder.getAccountNo());
//        collectBusiness.setBusinessBankCode(stakeholder.getBankCode());
//        collectBusiness.setBusinessBankAddress(stakeholder.getBankAddress());
//        collectBusiness.setBusinessBranchCode(stakeholder.getBranchCode());
//        collectBusiness.setBusinessTransferMethodType(stakeholder.getTransferMethodType());
//        collectBusiness.setBusinessPaymentMethodType(stakeholder.getPaymentMethodType());
//
//        collectBusiness.setBusinessRelationToBenefit(stakeholder.getRelationToBenefit());
//        collectBusiness.setBusinessRelationToMainInsure(stakeholder.getRelationToMainInsure());
//        collectBusiness.setBusinessRelationToOutInsure(stakeholder.getRelationToOutInsure());
//
//        collectBusiness.getExtraProperties().putAll(BizFieldUtil.fetchExtraBizField(stakeholder.getExtraProperties(), BizModelEnum.COLLECT_BUSINESS));
//
//        ClaimHintMsg claimHintMsg = querySyncHint(stakeholder.getRelatedId(), HintMsgType.COLLECT_BUSINESS, stakeholder.getId());
//        if (claimHintMsg != null) {
//            collectBusiness.setSyncHintMsg(JsonUtil.fromJson(claimHintMsg.getData(), new TypeReference<Map<String, String>>(){}));
//        }
//
//        return collectBusiness;
//    }

//    private ClaimStakeholder convertToEntity(CollectBusiness collectBusiness) {
//        ClaimStakeholder stakeholder = new ClaimStakeholder();
//        stakeholder.setId(collectBusiness.getId());
//        stakeholder.setTenantId(collectBusiness.getTenantId());
//        stakeholder.setBizIdentityCode(collectBusiness.getBizIdentityCode());
//        stakeholder.setRelatedId(collectBusiness.getRelatedId());
//        stakeholder.setPersonType(PersonTypeEnum.COLLECT_BUSINESS.getCode());
//
//        stakeholder.setName(collectBusiness.getLegalName());
//        stakeholder.setIdentityType(collectBusiness.getLegalIdentityType());
//        stakeholder.setIdentityNo(collectBusiness.getLegalIdentityNo());
//        stakeholder.setIdentityDatePeriod(collectBusiness.getLegalIdentityDatePeriod());
//        stakeholder.setPhone(collectBusiness.getLegalPhone());
//        stakeholder.setContactAddress(collectBusiness.getLegalContactAddress());
//
//        stakeholder.setBusinessName(collectBusiness.getBusinessName());
//        stakeholder.setBusinessIdentityDisc(collectBusiness.getBusinessIdentityDisc());
//        stakeholder.setBusinessIdentityType(collectBusiness.getBusinessIdentityType());
//        stakeholder.setBusinessIdentityNo(collectBusiness.getBusinessIdentityNo());
//        stakeholder.setBusinessIdentityDatePeriod(collectBusiness.getBusinessIdentityDatePeriod());
//        stakeholder.setBusinessPlace(collectBusiness.getBusinessPlace());
//        stakeholder.setBusinessRange(collectBusiness.getBusinessRange());
//
//        stakeholder.setAccountNo(collectBusiness.getBusinessAccountNo());
//        stakeholder.setBankCode(collectBusiness.getBusinessBankCode());
//        stakeholder.setBankAddress(collectBusiness.getBusinessBankAddress());
//        stakeholder.setBranchCode(collectBusiness.getBusinessBranchCode());
//        stakeholder.setTransferMethodType(collectBusiness.getBusinessTransferMethodType());
//        stakeholder.setPaymentMethodType(collectBusiness.getBusinessPaymentMethodType());
//
//        stakeholder.setRelationToBenefit(collectBusiness.getBusinessRelationToBenefit());
//        stakeholder.setRelationToMainInsure(collectBusiness.getBusinessRelationToMainInsure());
//        stakeholder.setRelationToOutInsure(collectBusiness.getBusinessRelationToOutInsure());
//
//        return stakeholder;
//    }
}
