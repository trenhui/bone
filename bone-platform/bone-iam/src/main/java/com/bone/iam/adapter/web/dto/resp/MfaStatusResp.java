package com.bone.iam.adapter.web.dto.resp;

import java.util.List;
import lombok.Data;

@Data
public class MfaStatusResp {

    private boolean enabled;
    private boolean enrolled;
    private List<String> methods;
}
