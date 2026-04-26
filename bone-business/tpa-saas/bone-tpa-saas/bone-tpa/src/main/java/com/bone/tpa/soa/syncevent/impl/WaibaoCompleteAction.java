package com.bone.tpa.soa.syncevent.impl;

import com.bone.tpa.api.enums.EventType;
import com.bone.tpa.api.request.SyncClaimWithEventRequest;
import com.bone.tpa.claim.flow.stage.InputStageService;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.soa.syncevent.BaseEventAction;
import com.bone.tpa.soa.syncevent.SyncEvnetAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class WaibaoCompleteAction extends BaseEventAction implements SyncEvnetAction {
    @Autowired
    private InputStageService inputStageService;

    @Override
    public EventType getEvent() {
        return EventType.外包录入完成tpa回调saas;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Map<String, Object> fire(SyncClaimWithEventRequest request) {
        Map<String, Object>  rs = new HashMap<>();
        Long claimNumber =  request.getClaimNumber();
        if( claimNumber == null){
            throw new IllegalArgumentException("claimNumber is null");
        }
        Claim claim =  claimRepository.findById(claimNumber);
        if( claim == null){
            throw new IllegalArgumentException("claim is null");
        }
        //保存数据
        syncFromTpaService.transfer(request);
        //变更外包状态
        inputStageService.waibaoInputSuccess(claim.getId());
        return rs;
    }
}
