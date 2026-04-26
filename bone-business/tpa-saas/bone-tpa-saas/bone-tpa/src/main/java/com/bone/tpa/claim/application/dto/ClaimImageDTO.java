package com.bone.tpa.claim.application.dto;

import com.bone.core.tenant.TenantAbstractEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ClaimImageDTO extends TenantAbstractEntity<ClaimImageDTO, Long> {

    @Schema(description = "关联赔案id")
    private Long relatedId;

    @Schema(description = "影像件唯一键")
    private String imageDetailId;
    @Schema(description = "影像件排序")
    private Integer imageIndex;

    @Schema(description = "影像件路径")
    private String imagePath;
    @Schema(description = "影像件名称")
    private String imageName;

    @Schema(description = "影像件类型")
    private String imageType;
    @Schema(description = "影像件类型(普康)")
    private String imagePkType;
    @Schema(description = "清晰类型")
    private Integer clearType;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "推送标识")
    private Integer pushFlag;

    /**
     * 是否来自个人影像库,0:否,1:是
     */
    private Integer fromPersonalImage;
}
