package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

//流程首页枚举
@Getter
public enum ProcessHeadPageEnum {

    GROUP_INSURANCE_OFFLINE_SIGN_HEAD("groupInsuranceOfflineSignHead", "首页_团险线下签收", ProcessTypeEnum.SIGN.getCode()),
    STANDARD_FIRST_AUDIT_HEAD("standardFirstAuditHead", "首页_标准初审", ProcessTypeEnum.FIRST_AUDIT.getCode()),
    STANDARD_ENTRY_HEAD("standardEntryHead", "首页_标准录入", ProcessTypeEnum.ENTRY.getCode()),
    STANDARD_QUALITY_CHECK_HEAD("standardQualityCheckHead", "首页_标准质检", ProcessTypeEnum.QUALITY_CHECK.getCode()),
    STANDARD_AUDIT_HEAD("standardAuditHead", "首页_标准审核", ProcessTypeEnum.AUDIT.getCode()),
    STANDARD_REVIEW_HEAD("standardReviewHead", "首页_标准复核", ProcessTypeEnum.REVIEW.getCode()),
    ;

    private final String code;
    private final String desc;
    private final byte processCode;//所属流程的code

    ProcessHeadPageEnum(String code, String desc, byte processCode) {
        this.code = code;
        this.desc = desc;
        this.processCode = processCode;
    }

    public static List<String> getProcessPageCodeList() {
        List<String> re = new ArrayList<>();
        for (ProcessHeadPageEnum item : ProcessHeadPageEnum.values()) {
            re.add(item.getCode());
        }
        return re;
    }
}
