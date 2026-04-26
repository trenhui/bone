/*
package com.bone.tpa.sdk.identityRule.claim.saveHook;

import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.identityRule.BizidentityHook;

public interface ClaimSaveHook extends BizidentityHook {
    static public final String domain = "claim";
    static public final String beanType = "claimSaveHook";

    default String getDomain(){
        return "claim";
    }
    default String getDomainDesc(){
        return "赔案";
    }

    default String getBeanType(){
        return "claimSaveHook";
    }

    default String getBeanTypeDesc(){
        return "赔案保存处理";
    }


    void onUpdateClaim(Claim claimExist , ClaimDetailObject claimDetailObject);
}
*/
