package com.bone.tpa.hook.inter;

import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.identityRule.BizidentityHook;

public abstract class ClaimPageSaveValidHook   {

    static public final String domain = "claim";
    static public final String beanType = "claimPageSaveValidHook";

    public String getDomain(){
        return domain;
    }
    public String getDomainDesc(){
        return "赔案";
    }

    public String getBeanType(){
        return beanType;
    }

    public String getBeanTypeDesc(){
        return "赔案页面保存校验";
    }

    abstract  public Boolean doEvent(Claim param);
}
