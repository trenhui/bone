package com.bone.tpa.api.request;

import lombok.Data;

import java.util.List;

@Data
public class ReturnToManualRequest {
    private List<Long> claimNos;
    private String node;
    private String userName;
    private String returnType;
    private String returnReason;
}
