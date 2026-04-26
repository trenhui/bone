package com.bone.tpa.audit.infrastructure.feign.response;

import lombok.Data;

import java.util.List;

@Data
public class TpaPersonInfoQueryResponse {
    private String primaryInsuredName;
    private String primaryInsuredIdNumber;
    private String primaryInsuredIdType;
    private List<String> primaryInsuredIdValidityPeriod;

    private String collectName;
    private String collectIdNumber;
    private String collectIdType;
    private List<String> collectIdValidityPeriod;
    private String collectContact;
    private String collectAddress;
    private String collectBankName;
    private String collectBankAccount;
}
