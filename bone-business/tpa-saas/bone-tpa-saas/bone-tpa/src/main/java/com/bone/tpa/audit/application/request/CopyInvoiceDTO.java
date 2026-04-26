package com.bone.tpa.audit.application.request;

import lombok.Data;

@Data
public class CopyInvoiceDTO {
    private Long claimId;
    private Long invoiceId;
    private String updateInvoiceNo;
}
