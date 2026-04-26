package com.bone.tpa.audit.application.request;


import lombok.Data;

import java.util.List;

@Data
public class BatchUpdateDutyDTO {
    private Long claimId;
    private String policyNo;
    private List<Long> invoiceIds;
    private List<String> dutyIds;
}
