package com.bone.lowcode.infra.application.upload;

import lombok.Data;

@Data
public class UploadCommonDto {

    /**
     * 导入业务类型，目前只有optionSet
     */
    private String bizType;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * json序列化的入参
     */
    private String data;

}
