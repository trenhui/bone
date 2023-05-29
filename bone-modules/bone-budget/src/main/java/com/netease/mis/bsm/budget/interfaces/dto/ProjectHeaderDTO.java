package com.netease.mis.bsm.budget.interfaces.dto;

import com.bone.core.tenant.TenantAbstractDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import java.util.Date;
import java.math.BigDecimal;

/**
* 项目单头DTO
*
* @author 梅山源码
*/
@Data
@Schema(description = "项目单头DTO")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProjectHeaderDTO extends TenantAbstractDTO<Long> {

        /**
         * 项目编号
         */
         @Schema(name = "项目编号")
        private String projectNumber;
        /**
         * 核算主体ID
         */
         @Schema(name = "核算主体ID")
        private Long accEntityId;
        /**
         * 成本中心ID
         */
         @Schema(name = "成本中心ID")
        private Long costCenterId;
        /**
         * 部门ID
         */
         @Schema(name = "部门ID")
        private Long unitId;
        /**
         * 提单人
         */
         @Schema(name = "提单人")
        private Long employeeId;
        /**
         * 制单人(不同于提单人就是代提人)
         */
         @Schema(name = "制单人(不同于提单人就是代提人)")
        private Long creatorEmployeeId;
        /**
         * 描述
         */
         @Schema(name = "描述")
        private String description;
        /**
         * 业务币种
         */
         @Schema(name = "业务币种")
        private String bizCurrencyCode;
        /**
         * 预算金额
         */
         @Schema(name = "预算金额")
        private BigDecimal bizAmount;
        /**
         * 原始预算金额
         */
         @Schema(name = "原始预算金额")
        private BigDecimal bizAmountOrigin;
        /**
         * 单据状态：草稿、流程中、挂起、已完结、拒绝
         */
         @Schema(name = "单据状态：草稿、流程中、挂起、已完结、拒绝")
        private String docStatus;
        /**
         * 财务审核，未审核，已审核，已复核
         */
         @Schema(name = "财务审核，未审核，已审核，已复核")
        private String docAuditStatus;
        /**
         * 单据页可编辑的状态，可全部编辑，审核可编辑，退回可编辑，只读
         */
         @Schema(name = "单据页可编辑的状态，可全部编辑，审核可编辑，退回可编辑，只读")
        private String docEditStatus;
        /**
         * 删除标识:0未删除，1删除
         */
         @Schema(name = "删除标识:0未删除，1删除")
        private String delFlag;
        /**
         * 业务币种->本位币种汇率
         */
         @Schema(name = "业务币种->本位币种汇率")
        private BigDecimal biz2magExchangeRate;
        /**
         * 本位币
         */
         @Schema(name = "本位币")
        private String magCurrencyCode;
        /**
         * 本位币金额
         */
         @Schema(name = "本位币金额")
        private BigDecimal magAmount;
        /**
         * 项目名称
         */
         @Schema(name = "项目名称")
        private String projectName;
        /**
         * 项目类型id
         */
         @Schema(name = "项目类型id")
        private Long projectTypeId;
        /**
         * 是否为临时项目0否1是
         */
         @Schema(name = "是否为临时项目0否1是")
        private Integer tempProjectFlag;
        /**
         * 维值
         */
         @Schema(name = "维值")
        private Long dim1ValueId;
        /**
         * 维值
         */
         @Schema(name = "维值")
        private Long dim2ValueId;
        /**
         * 维值
         */
         @Schema(name = "维值")
        private Long dim3ValueId;
        /**
         * 维值
         */
         @Schema(name = "维值")
        private Long dim4ValueId;
        /**
         * 维值
         */
         @Schema(name = "维值")
        private Long dim5ValueId;
        /**
         * 维值
         */
         @Schema(name = "维值")
        private Long dim6ValueId;
        /**
         * 维值
         */
         @Schema(name = "维值")
        private Long dim7ValueId;
        /**
         * 维值
         */
         @Schema(name = "维值")
        private Long dim8ValueId;
        /**
         * 维值
         */
         @Schema(name = "维值")
        private Long dim9ValueId;
        /**
         * 维值
         */
         @Schema(name = "维值")
        private Long dim10ValueId;
        /**
         * 项目开始时间
         */
         @Schema(name = "项目开始时间")
        private Date projectStartTime;
        /**
         * 项目结束时间
         */
         @Schema(name = "项目结束时间")
        private Date projectEndTime;
        /**
         * 项目关闭时间
         */
         @Schema(name = "项目关闭时间")
        private Date projectCloseTime;
        /**
         * 项目信息来源，EMP，OA
         */
         @Schema(name = "项目信息来源，EMP，OA")
        private String source;
        /**
         * 原始本位币金额
         */
         @Schema(name = "原始本位币金额")
        private BigDecimal magAmountOrigin;
        /**
         * 业务币种->本位币种汇率原始
         */
         @Schema(name = "业务币种->本位币种汇率原始")
        private BigDecimal biz2magExchangeRateOrigin;
        /**
         * 项目层级：0父项目，1子项目
         */
         @Schema(name = "项目层级：0父项目，1子项目")
        private Integer projectLevel;
        /**
         * 父项目id
         */
         @Schema(name = "父项目id")
        private Long parentProjectId;
        /**
         * 三方流程编号
         */
         @Schema(name = "三方流程编号")
        private String partnerProcessId;
        /**
         * 项目负责人（多个id，逗号隔开）
         */
         @Schema(name = "项目负责人（多个id，逗号隔开）")
        private String projectOwners;
}
