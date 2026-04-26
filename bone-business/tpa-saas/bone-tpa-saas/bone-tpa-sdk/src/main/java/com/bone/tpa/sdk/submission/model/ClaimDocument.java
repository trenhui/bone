package com.bone.tpa.sdk.submission.model;

import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.domain.extension.ExtensibleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * ClaimDocument 影像件模型
 */
@Table("ss_claim_document")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ClaimDocument extends ExtensibleObject<ClaimDocument, Long> {

    @Schema(description = "影像件唯一标识")
    private Long id; // 影像件唯一标识

    @Schema(description = "赔案编号")
    private String claimNo; // 赔案编号

    @Schema(description = "影像件名称")
    private String docName; // 影像件名称

    @Schema(description = "影像件类型")
    private String docType; // 影像件类型

    @Schema(description = "影像件序号")
    private Integer sortOrder; // 影像件序号

    @Schema(description = "影像件存储路径")
    private String imageUrl; // 影像件存储路径

}
