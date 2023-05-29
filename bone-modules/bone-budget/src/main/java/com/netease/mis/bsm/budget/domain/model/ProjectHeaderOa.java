package com.netease.mis.bsm.budget.domain.model;

import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;
import java.util.*;
import java.util.Date;
import java.math.BigDecimal;
import org.springframework.data.relational.core.mapping.Table;

/**
 * OA项目单头
 *
 * @author 梅山源码
 */
@Table("project_header_oa")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectHeaderOa extends TenantAbstractEntity<Long> {
        /**
         * 项目编号
         */
    private String projectNumber;
        /**
         * 核算主体ID
         */
    private Long accEntityId;
        /**
         * 核算主体Code
         */
    private String accEntityCode;
        /**
         * 成本中心ID
         */
    private Long costCenterId;
        /**
         * 成本中心Code
         */
    private String costCenterCode;
        /**
         * 部门ID
         */
    private Long unitId;
        /**
         * 部门Code
         */
    private String unitCode;
        /**
         * 提单人
         */
    private Long employeeId;
        /**
         * 制单人(不同于提单人就是代提人)
         */
    private Long creatorEmployeeId;
        /**
         * 描述
         */
    private String description;
        /**
         * 业务币种
         */
    private String bizCurrencyCode;
        /**
         * 预算金额
         */
    private BigDecimal bizAmount;
        /**
         * 原始预算金额
         */
    private BigDecimal bizAmountOrigin;
        /**
         * 申请原因：新增NEW，追加ADD
         */
    private String applyReason;
        /**
         * 单据状态：草稿、流程中、挂起、已完结、拒绝
         */
    private String docStatus;
        /**
         * 财务审核，未审核，已审核，已复核
         */
    private String docAuditStatus;
        /**
         * 单据页可编辑的状态，可全部编辑，审核可编辑，退回可编辑，只读
         */
    private String docEditStatus;
        /**
         * 创建人工号
         */
    private String createByCode;
        /**
         * 申请时间
         */
    private Date applyDate;
        /**
         * 删除标识:0未删除，1删除
         */
    private String delFlag;
        /**
         * 业务币种->本位币种汇率
         */
    private BigDecimal biz2magExchangeRate;
        /**
         * 本位币
         */
    private String magCurrencyCode;
        /**
         * 本位币金额
         */
    private BigDecimal magAmount;
        /**
         * 追加/新建业务币金额
         */
    private BigDecimal addBizAmount;
        /**
         * 追加/新建本位币金额
         */
    private BigDecimal addMagAmount;
        /**
         * 项目名称
         */
    private String projectName;
        /**
         * 项目类型id
         */
    private Long projectTypeId;
        /**
         * 项目类型name
         */
    private String projectTypeName;
        /**
         * 产品线code
         */
    private String productCode;
        /**
         * 是否为临时项目0否1是
         */
    private Integer tempProjectFlag;
        /**
         * 维值
         */
    private Long dim1ValueId;
        /**
         * 维值
         */
    private Long dim2ValueId;
        /**
         * 维值
         */
    private Long dim3ValueId;
        /**
         * 维值
         */
    private Long dim4ValueId;
        /**
         * 维值
         */
    private Long dim5ValueId;
        /**
         * 维值
         */
    private Long dim6ValueId;
        /**
         * 维值
         */
    private Long dim7ValueId;
        /**
         * 维值
         */
    private Long dim8ValueId;
        /**
         * 维值
         */
    private Long dim9ValueId;
        /**
         * 维值
         */
    private Long dim10ValueId;
        /**
         * 项目开始时间
         */
    private Date projectStartTime;
        /**
         * 项目结束时间
         */
    private Date projectEndTime;
        /**
         * 项目关闭时间
         */
    private Date projectCloseTime;
        /**
         * 项目信息来源，EMP，OA
         */
    private String source;
        /**
         * 原始本位币金额
         */
    private BigDecimal magAmountOrigin;
        /**
         * 业务币种->本位币种汇率原始
         */
    private BigDecimal biz2magExchangeRateOrigin;
        /**
         * 项目层级：0父项目，1子项目
         */
    private Integer projectLevel;
        /**
         * 父项目id
         */
    private Long parentProjectId;
        /**
         * 三方流程编号
         */
    private String partnerProcessId;
        /**
         * 项目负责人（多个id，逗号隔开）
         */
    private String projectOwners;
        /**
         * 项目负责人（多个code，逗号隔开）
         */
    private String projectOwnersCode;
        /**
         * 项目成员（多个id，逗号隔开）
         */
    private String projectEmployeeIds;
        /**
         * 项目成员（多个工号，逗号隔开）
         */
    private String projectEmployeesCode;
        /**
         * 同步状态，success：成功，error：失败
         */
    private String syncStatus;
        /**
         * 接口同步消息，如果失败记录详细错误信息
         */
    private String syncMessage;
}

