package com.bone.tpa.hook.inter;

import com.bone.tpa.hook.vo.ClaimUpdateHookParam;

public  abstract class ClaimPageSaveAfterHook {
    static public final String domain = "claim";
    static public final String beanType = "claimPageSaveAfterHook";
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
        return "页面赔案保存处理";
    }
   abstract public Boolean doEvent(ClaimUpdateHookParam param);

}
