package com.bone.tpa.submission.application.dto;

import com.bone.core.domain.extension.ExtensibleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ClaimDocumentDTO extends ExtensibleObject<ClaimDocumentDTO, Long> {

    @Schema(description = "影像件唯一标识")
    private Long id;

    @Schema(description = "赔案编号")
    private String claimNo;

    @Schema(description = "影像件名称")
    private String docName;

    @Schema(description = "影像件类型")
    private String docType;

    @Schema(description = "影像件序号")
    private Integer sortOrder;

    @Schema(description = "影像件存储路径")
    private String imageUrl;
}
