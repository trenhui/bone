package com.netease.mis.bsm.budget.interfaces.dto;

import com.bone.core.tenant.TenantAbstractDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.util.Date;
import java.math.BigDecimal;

/**
* 事件DTO
*
* @author 梅山源码
*/
@Data
@Schema(description = "事件DTO")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProjectEventDTO extends TenantAbstractDTO<Long> {

        /**
         * 事件编号
         */
         @Schema(name = "事件编号")
        private String eventNumber;
        /**
         * 事件名称
         */
         @Schema(name = "事件名称")
        private String eventName;
        /**
         * 预算金额
         */
         @Schema(name = "预算金额")
        private BigDecimal bizAmount;
        /**
         * 业务币种
         */
         @Schema(name = "业务币种")
        private String bizCurrencyCode;
        /**
         * 事件开始时间
         */
         @Schema(name = "事件开始时间")
        private Date eventStartTime;
        /**
         * 事件结束时间
         */
         @Schema(name = "事件结束时间")
        private Date eventEndTime;
        /**
         * 删除标识:0未删除，1删除
         */
         @Schema(name = "删除标识:0未删除，1删除")
        private String delFlag;
        /**
         * 依附子项目id
         */
         @Schema(name = "依附子项目id")
        private Long projectId;
}
