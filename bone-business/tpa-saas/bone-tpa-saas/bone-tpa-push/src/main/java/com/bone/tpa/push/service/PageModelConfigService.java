package com.bone.tpa.push.service;

import com.bone.metadata.sdk.enums.FieldModelDefine;
import com.bone.tpa.facade.vo.OptionSetDTO;

public interface PageModelConfigService {
    OptionSetDTO getOptionSet(String pageBizCode,
                              String bizIdentityCode,
                              FieldModelDefine domainDefine, String code);
}
