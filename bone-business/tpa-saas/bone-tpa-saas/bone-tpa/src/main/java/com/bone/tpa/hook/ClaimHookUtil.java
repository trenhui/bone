package com.bone.tpa.hook;

import com.bone.tpa.core.hook.BizHookFactory;
import com.bone.tpa.hook.impl.claim.YongchengPageSaveValidHook;
import com.bone.tpa.hook.inter.ClaimPageSaveAfterHook;
import com.bone.tpa.hook.inter.SyncFromTpaPreDealHook;
import com.bone.tpa.hook.inter.SyncToTpaAfterDealHook;
import com.bone.tpa.sdk.identityRule.BizidentityHook;
import com.bone.tpa.sdk.identityRule.invoice.update.OnSaveInvoiceHook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ClaimHookUtil {
    @Autowired
    private BizHookFactory hookFactory;


    public List<ClaimPageSaveAfterHook> getClaimSaveHookList(String bizIdentityCode){
        List<ClaimPageSaveAfterHook> rs = new ArrayList<>();

        List<BizidentityHook> matchedList =   hookFactory.getRuntimeHooks(ClaimPageSaveAfterHook.domain, ClaimPageSaveAfterHook.beanType,bizIdentityCode);
        for(BizidentityHook hook : matchedList){
            if( hook instanceof ClaimPageSaveAfterHook){
                rs.add((ClaimPageSaveAfterHook)hook);
            }
        }
        return  rs;
    }

    //SyncFromTpaPreDealHook
    public List<SyncFromTpaPreDealHook> getSyncFromTpaPreDealHookList(String bizIdentityCode){
        List<SyncFromTpaPreDealHook> rs = new ArrayList<>();

        List<BizidentityHook> matchedList =   hookFactory.getRuntimeHooks(SyncFromTpaPreDealHook.domain,
                SyncFromTpaPreDealHook.beanType,bizIdentityCode);
        for(BizidentityHook hook : matchedList){
            if( hook instanceof  SyncFromTpaPreDealHook){
                rs.add((SyncFromTpaPreDealHook)hook);
            }
        }
        return  rs;
    }

    public List<YongchengPageSaveValidHook> getYongchengPageSaveValidHookList(String bizIdentityCode){
        List<YongchengPageSaveValidHook> rs = new ArrayList<>();

        List<BizidentityHook> matchedList =   hookFactory.
                getRuntimeHooks(YongchengPageSaveValidHook.domain,
                        YongchengPageSaveValidHook.beanType,bizIdentityCode);
        for(BizidentityHook hook : matchedList){
            if( hook instanceof  YongchengPageSaveValidHook){
                rs.add((YongchengPageSaveValidHook)hook);
            }
        }
        return  rs;
    }

    public List<OnSaveInvoiceHook> getOnSaveInvoiceHookList(String bizIdentityCode){
        List<OnSaveInvoiceHook> rs = new ArrayList<>();

        List<BizidentityHook> matchedList =   hookFactory.
                getRuntimeHooks(OnSaveInvoiceHook.domain,
                        OnSaveInvoiceHook.beanType,bizIdentityCode);
        for(BizidentityHook hook : matchedList){
            if( hook instanceof  OnSaveInvoiceHook){
                rs.add((OnSaveInvoiceHook)hook);
            }
        }
        return  rs;
    }

    //SyncToTpaAfterDealHook
    public List<SyncToTpaAfterDealHook> getSyncToTpaAfterDealHookList(String bizIdentityCode){
        List<SyncToTpaAfterDealHook> rs = new ArrayList<>();

        List<BizidentityHook> matchedList =   hookFactory.getRuntimeHooks(SyncToTpaAfterDealHook.domain,
                SyncToTpaAfterDealHook.beanType,bizIdentityCode);
        for(BizidentityHook hook : matchedList){
            if( hook instanceof  SyncToTpaAfterDealHook){
                rs.add((SyncToTpaAfterDealHook)hook);
            }
        }
        return  rs;
    }


}
