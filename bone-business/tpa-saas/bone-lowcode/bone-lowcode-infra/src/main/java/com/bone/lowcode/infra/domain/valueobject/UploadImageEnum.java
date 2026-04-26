package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//所有上传影像组件的枚举
@Getter
public enum UploadImageEnum {
    SIGN_DETAIL_UPLOAD_IMAGE("signDetailUploadImage", "签收批次详情页-导入影像件", "签收批次详情页"),
    ;
    private final String code;
    private final String desc;

    /**
     * 适用页面
     */
    private final String applicablePage;

    UploadImageEnum(String code, String desc, String applicablePage) {
        this.code = code;
        this.desc = desc;
        this.applicablePage = applicablePage;
    }

    public static List<UploadImageEnum> list1() {
        return new ArrayList<>(Arrays.asList(SIGN_DETAIL_UPLOAD_IMAGE));
    }
}
