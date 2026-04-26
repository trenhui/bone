package com.bone.tpa.claim.application.dto;

import com.bone.core.tenant.TenantAbstractEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * ss_claim DO
 *
 * @author 0
 */
@Data
public class FileUploadRecordDTO extends TenantAbstractEntity<FileUploadRecordDTO, Long> {

    @Schema(description = "上传场景")
    private String uploadScene;

//    @Schema(description = "关联模型名")
//    private String relatedModel;

//    @Schema(description = "关联表id")
//    private Long relatedId;

    @Schema(description = "文件类型")
    private String fileType;

    @Schema(description = "文件路径")
    private String filePath;

    @Schema(description = "文件名称")
    private String fileName;

    @Schema(description = "导入方式")
    private String importType;

    @Schema(description = "校验方式")
    private String checkType;

    @Schema(description = "上传结果")
    private String sameFileRule;

    @Schema(description = "上传结果")
    private String result;

    @Schema(description = "额外信息")
    private String extraInfo;

    @Schema(description = "操作者")
    private String operator;
}
