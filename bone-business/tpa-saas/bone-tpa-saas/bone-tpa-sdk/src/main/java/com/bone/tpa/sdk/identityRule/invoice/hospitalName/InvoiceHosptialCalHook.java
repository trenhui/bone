package com.bone.tpa.sdk.identityRule.invoice.hospitalName;

import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.identityRule.BizidentityHook;

public abstract class InvoiceHosptialCalHook   {
    static public final String domain = "invoice";
    static public final String beanType = "invoiceDefaultHospitalName";
    public String getDomain(){
        return "invoice";
    }
    public String getDomainDesc(){
        return "发票";
    }

    public String getBeanType(){
        return "invoiceDefaultHospitalName";
    }

    public String getBeanTypeDesc(){
        return "发票默认医院名称";
    }


    public   abstract String doEvent(String bizIdentityCode);
}
