package com.netease.mis.bsm.budget.interfaces.dto;

import com.bone.core.tenant.TenantAbstractDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
* 单据附件DTO
*
* @author 梅山源码
*/
@Data
@Schema(description = "单据附件DTO")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AttachmentDTO extends TenantAbstractDTO<Long> {

        /**
         * 单据：费用单类型、付款单，对应到哪张表里找数据
         */
         @Schema(name = "单据：费用单类型、付款单，对应到哪张表里找数据")
        private String docType;
        /**
         * 单据ID
         */
         @Schema(name = "单据ID")
        private Long docId;
        /**
         * 对应的文件信息
         */
         @Schema(name = "对应的文件信息")
        private String fileCode;
        /**
         * 文件名称，冗余存储
         */
         @Schema(name = "文件名称，冗余存储")
        private String fileName;
        /**
         * 文件地址
         */
         @Schema(name = "文件地址")
        private String fileAddress;
}
