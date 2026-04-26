package com.bone.lowcode.infra.domain.valueobject;

import lombok.Getter;

//上传组件类型枚举
@Getter
public enum UploadComponentEnum {
    UPLOAD_DATA((byte) 1, "导入数据excel"),
    UPLOAD_IMAGE((byte) 2, "导入影像件"),
    UPLOAD_PICTURE((byte) 3, "导入图片"),
    UPLOAD_ATTACHMENT((byte) 4, "导入附件"),
    ;
    private final byte code;
    private final String desc;

    UploadComponentEnum(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
