package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

//流程首页枚举
@Getter
public enum ProcessListPageEnum {

    SIGN_LIST("signList", "签收批次列表页", ProcessTypeEnum.SIGN.getCode()),
    SIGN_DETAIL_LIST("signDetailList", "签收批次详情页", ProcessTypeEnum.SIGN.getCode()),
    FIRST_AUDIT_LIST("firstAuditList", "初审列表页", ProcessTypeEnum.FIRST_AUDIT.getCode()),
    ENTRY_LIST("entryList", "录入列表页", ProcessTypeEnum.ENTRY.getCode()),
    QUALITY_CHECK_LIST("qualityCheckList", "质检列表页", ProcessTypeEnum.QUALITY_CHECK.getCode()),
    AUDIT_LIST("auditList", "审核列表页", ProcessTypeEnum.AUDIT.getCode()),
    REVIEW_LIST("reviewList", "复核列表页", ProcessTypeEnum.REVIEW.getCode()),
    CLAIM_DISTRIBUTE_LIST("claimDistributeList", "作业管理分配列表页", null),
    CLAIM_HAND_OVER_LIST("claimHandOverList", "作业管理转交列表页", null),
    GROUP_POLICY_LIST("groupPolicyList", "团险保单列表页", null),
    ;

    private final String code;
    private final String desc;
    private final Byte processCode;//所属流程的code

    ProcessListPageEnum(String code, String desc, Byte processCode) {
        this.code = code;
        this.desc = desc;
        this.processCode = processCode;
    }

    public static List<String> getProcessPageCodeList() {
        List<String> re = new ArrayList<>();
        for (ProcessListPageEnum item : ProcessListPageEnum.values()) {
            re.add(item.getCode());
        }
        return re;
    }
}
