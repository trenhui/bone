package com.bone.tpa.claim.application.request;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FileUploadRequestExtend extends FileUploadRequest {

    private String policyNo;

    private Byte operationType;
}
