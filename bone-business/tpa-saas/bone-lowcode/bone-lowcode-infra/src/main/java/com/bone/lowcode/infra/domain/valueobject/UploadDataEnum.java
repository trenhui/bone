package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//所有上传数据组件的枚举
@Getter
public enum UploadDataEnum {
    NEW_SIGN_UPLOAD_PEOPLE("newSignUploadPeople", "新批次签收-导入人员", "新批次签收页"),
    PERSONAL_QUOTA_UPLOAD("personalQuotaInitialize", "个人专属额度-初始化", "保单规则-个人额度"),
    PERSONAL_QUOTA_CHANGE("personalQuotaChange", "个人专属额度-额度加减", "保单规则-个人额度"),
    PERSONAL_QUOTA_REMOVE_PEOPLE("personalQuotaRemovePeople", "个人专属额度-减人", "保单规则-个人额度"),
    ;
    private final String code;
    private final String desc;

    /**
     * 适用页面
     */
    private final String applicablePage;

    UploadDataEnum(String code, String desc, String applicablePage) {
        this.code = code;
        this.desc = desc;
        this.applicablePage = applicablePage;
    }

    public static List<String> getCodeList() {
        List<String> res = new ArrayList<>();
        for (UploadDataEnum value : UploadDataEnum.values()) {
            res.add(value.getCode());
        }
        return res;
    }

    /**
     * 配置页面的
     */
    public static List<UploadDataEnum> list1() {
        return new ArrayList<>(Arrays.asList(NEW_SIGN_UPLOAD_PEOPLE));
    }

    /**
     * 固定页面的
     */
    public static List<UploadDataEnum> list2() {
        return new ArrayList<>(Arrays.asList(PERSONAL_QUOTA_UPLOAD, PERSONAL_QUOTA_CHANGE, PERSONAL_QUOTA_REMOVE_PEOPLE));
    }
}
