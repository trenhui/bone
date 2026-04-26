package com.bone.tpa.claim.application.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class FileUploadRequest implements Serializable {

    @Schema(description = "租户id")
    private Long tenantId;

    @Schema(description = "关联数据模型id")
    //把这个字段提到外面去，剩下四个当做基类
    private Long relatedId;

    /**
     * 数据导入组件id，用于查询调用该上传组件的具体配置
     */
    @Schema(description = "数据导入组件配置id")
    private Long configId;

    @Schema(description = "上传组件类型")
    private Byte type;

    @Schema(description = "导入方式")
    private Integer importType;

    @Schema(description = "文件上传路径")
    private List<String> fileUrlList;

}
