package com.bone.tpa.soa.syncevent.impl;

import com.bone.tpa.api.enums.EventType;
import com.bone.tpa.api.request.SyncClaimWithEventRequest;
import com.bone.tpa.claim.application.ClaimImageApplicationService;
import com.bone.tpa.soa.syncevent.BaseEventAction;
import com.bone.tpa.soa.syncevent.SyncEvnetAction;
import com.bone.tpa.task.impl.AutoInputTrigger;
import com.bone.tpa.task.impl.InputDealerApplyTrigger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class TpaSignToSaasInput  extends BaseEventAction implements SyncEvnetAction {

    @Autowired
    InputDealerApplyTrigger inputDealerApplyTrigger;
    @Autowired
    AutoInputTrigger autoInputTrigger;
    @Autowired
    ClaimImageApplicationService claimImageApplicationService;
    @Autowired
    TpaSignClaimToSaas tpaSignClaimToSaas;
    @Override
    public EventType getEvent() {
        return EventType.tpa赔案跳过初审下发到saas录入;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Map<String, Object> fire(SyncClaimWithEventRequest request) {

        return tpaSignClaimToSaas.fire(request);
     /*   Map<String, Object>  rs = new HashMap<>();

        //存储数据
        Long claimNumber =  request.getClaimNumber();
        if( claimNumber == null){
            throw new IllegalArgumentException("claimNumber is null");
        }
        commonLogService.addClaimLogAsync(request.getClaimNumber(), CommonLogType.FROM_TPA_LOG,
                "tpa赔案跳过初审下发到saas录入,claimNumber:{},data:{}", request.getClaimNumber(),
                JSONObject.toJSONString(request, SerializerFeature.DisableCircularReferenceDetect));
        Claim checkExist =  claimRepository.findById(claimNumber);
        if( checkExist != null){
            throw new IllegalArgumentException("claim is aleady exist");
        }

        //获取保司影像件信息
        List<InsuranceCompanyImageVO> insuranceCompanyImageVOList = claimImageApplicationService.getCompanyImageVO(request.getClaimInfo().getInsuranceName());

        //保司分类, 普康分类
        Map<String, String> imageTypeMap = insuranceCompanyImageVOList.stream().collect(Collectors.toMap(InsuranceCompanyImageVO::getImageClassifyCode, InsuranceCompanyImageVO::getImageMapCode));

        if(request.getClaimInfo()!=null && PkListUtil.isNotEmpty(request.getClaimInfo().getImageList())){
            for (ImageInfo imageInfo : request.getClaimInfo().getImageList()) {
                if (imageTypeMap.containsKey(imageInfo.getImageType())) {
                    imageInfo.setImagePkType(imageTypeMap.get(imageInfo.getImageType()));
                }
            }
        }


        //保存数据
        syncFromTpaService.transfer(request);
        commonLogService.addClaimLogAsync(request.getClaimNumber(), CommonLogType.FROM_TPA_LOG,
                "tpa赔案跳过初审下发到saas录入,claimNumber:{},保存数据结束", request.getClaimNumber());
        // 做状态变更,并且异步通知tpa状态变成ocr录入中
        //为啥要异步，因为tpa会更新claim 的信息，调用tpa通知接口也会更新claim 的状态，容易造成死锁
        claimActionService.completePreExameWithSyncSet(claimNumber,true);
        checkExist =  claimRepository.findById(claimNumber);
        rs.put(BIZ_IDENTITY_CODE, checkExist.getBizIdentityCode());
        rs.put(TENANT_ID,checkExist.getTenantId());
        ClaimStatusEnum statusEnum = ClaimStatusEnum.getByCode(checkExist.getStatus(),checkExist.getStatusSub());
        rs.put(CLAIM_STATUS, statusEnum.getCode());
        rs.put(CLAIM_STATUS_DESC, statusEnum.getValue());

        commonLogService.addClaimLogAsync(request.getClaimNumber(), CommonLogType.FROM_TPA_LOG,
                "tpa赔案跳过初审下发到saas录入,claimNumber:{},返回:{}", request.getClaimNumber(),
                JSONObject.toJSONString(rs, SerializerFeature.DisableCircularReferenceDetect));
        return rs;*/
    }
}
