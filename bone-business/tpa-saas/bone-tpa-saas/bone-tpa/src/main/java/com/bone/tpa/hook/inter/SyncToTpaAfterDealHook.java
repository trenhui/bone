package com.bone.tpa.hook.inter;

import com.bone.tpa.api.vo.ClaimDetailSyncVO;
import com.bone.tpa.sdk.identityRule.BizidentityHook;

public abstract class SyncToTpaAfterDealHook    {
    static public final String domain = "syncToTpa";
    static public final String beanType = "syncToTpaAfterDealHook";

    public String getDomain(){
        return domain;
    }
    public String getDomainDesc(){
        return "同步数据到tpa";
    }

    public String getBeanType(){
        return beanType;
    }

    public String getBeanTypeDesc(){
        return "同步数据到tpa后置处理";
    }

    abstract    public Boolean doEvent(ClaimDetailSyncVO param);
}
