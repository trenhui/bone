package com.bone.tpa.sdk.identityRule.invoice.update;

import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.identityRule.BizidentityHook;

public abstract class OnSaveInvoiceHook  {
    static public final String domain = "invoice";
    static public final String beanType = "invoiceSaveHook";

   public String getDomain(){
       return "invoice";
   }
    public String getDomainDesc(){
        return "发票";
    }

    public String getBeanType(){
        return "invoiceSaveHook";
    }

    public String getBeanTypeDesc(){
        return "发票保存处理";
    }

    abstract public Boolean doEvent(ClaimInvoice param);
}
