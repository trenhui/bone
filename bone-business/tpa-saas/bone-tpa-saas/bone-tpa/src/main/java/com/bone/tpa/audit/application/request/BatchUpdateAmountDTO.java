package com.bone.tpa.audit.application.request;

import lombok.Data;

import java.util.List;

@Data
public class BatchUpdateAmountDTO {
    private Long claimId;
    private List<Long> invoiceIds;
}
