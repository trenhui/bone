package com.bone.tpa.push.bean;

import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import lombok.Data;

import java.util.List;

@Data
public class InvoicePropertyBean {
    private List<ClaimInvoice> claimInvoices;
    private String dutyId;
    private String treatmentType;
}
