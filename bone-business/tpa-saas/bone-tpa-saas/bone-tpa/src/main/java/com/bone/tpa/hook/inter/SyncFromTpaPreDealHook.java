package com.bone.tpa.hook.inter;

import com.bone.tpa.api.vo.ClaimDetailSyncVO;
import com.bone.tpa.hook.vo.FromTpaPreHookParam;
import com.bone.tpa.sdk.identityRule.BizidentityHook;

public abstract class SyncFromTpaPreDealHook   {
    static public final String domain = "syncFromTpa";
    static public final String beanType = "syncFromTpaPredealHook";

    public String getDomain(){
        return domain;
    }
    public String getDomainDesc(){
        return "tpa同步数据";
    }

    public String getBeanType(){
        return beanType;
    }

    public String getBeanTypeDesc(){
        return "tpa同步数据同步前处理";
    }


  abstract   public Boolean doEvent(FromTpaPreHookParam param);

}
