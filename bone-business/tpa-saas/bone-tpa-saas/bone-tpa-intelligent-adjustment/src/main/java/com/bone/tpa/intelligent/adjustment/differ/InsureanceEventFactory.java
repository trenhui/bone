package com.bone.tpa.intelligent.adjustment.differ;

import com.bone.tpa.core.hook.BizHookFactory;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.dao.ClaimRepository;
import com.bone.tpa.sdk.identityRule.invoice.hospitalName.InvoiceHosptialCalHook;
import com.bone.tpa.sdk.util.SpringContextUtils;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class InsureanceEventFactory {
    /*   @Resource(name="claimDefaultEventHandler")
       private BaseClaimEventHandler baseClaimEventHandler;*/
    @Resource
    BizHookFactory hookFactory;
    @Autowired
    private ClaimRepository claimRepository;

    public String  getDefaultHospitalName(Long claimId){
        Claim claim =  claimRepository.findById(claimId);
        if(claim == null){
            return null;
        }
        String bizIddentityCode = claim.getBizIdentityCode();
        InvoiceHosptialCalHook hook = hookFactory.getInvoiceHosptialCalHook(bizIddentityCode);
        if( hook== null){
            return  null;
        }
        return hook.doEvent(bizIddentityCode);
    }
}
