package com.bone.lowcode.studio.interfaces.dto;

import com.bone.core.tenant.TenantAbstractDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
* 表单DTO
*
* @author 梅山源码
*/
@Data
@Schema(description = "表单DTO")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class FormDTO extends TenantAbstractDTO<Long> {

        /**
         * 名称
         */
         @Schema(name = "名称")
        private String name;
        /**
         * 编码
         */
         @Schema(name = "编码")
        private String code;
        /**
         * 内容
         */
         @Schema(name = "内容")
        private String content;
        /**
         * 所属应用
         */
         @Schema(name = "所属应用")
        private Long appId;
        /**
         * 备注
         */
         @Schema(name = "备注")
        private String remark;
        /**
         * 状态（0New1Runing2Offline）
         */
         @Schema(name = "状态（0New1Runing2Offline）")
        private Byte status;
}
