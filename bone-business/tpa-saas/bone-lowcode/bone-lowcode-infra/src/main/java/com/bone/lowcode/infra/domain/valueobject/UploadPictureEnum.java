package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//所有上传图片组件的枚举
@Getter
public enum UploadPictureEnum {
    FIRST_AUDIT_DETAIL_UPLOAD_PICTURE("firstAuditDetailUploadPicture", "初审详情页补充影像件-导入图片", "初审详情页"),
    ;
    private final String code;
    private final String desc;

    /**
     * 适用页面
     */
    private final String applicablePage;

    UploadPictureEnum(String code, String desc, String applicablePage) {
        this.code = code;
        this.desc = desc;
        this.applicablePage = applicablePage;
    }

    /**
     * 配置页面的
     */
    public static List<UploadPictureEnum> list1() {
        return new ArrayList<>(Arrays.asList(FIRST_AUDIT_DETAIL_UPLOAD_PICTURE));
    }
}
