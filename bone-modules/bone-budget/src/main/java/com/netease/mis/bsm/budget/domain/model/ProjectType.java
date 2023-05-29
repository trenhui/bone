package com.netease.mis.bsm.budget.domain.model;

import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;
import java.util.*;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 项目类型
 *
 * @author 梅山源码
 */
@Table("project_type")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectType extends TenantAbstractEntity<Long> {
        /**
         * 项目类型code
         */
    private String projectTypeCode;
        /**
         * 项目类型名称
         */
    private String projectTypeName;
        /**
         * 删除标识:0未删除，1删除
         */
    private String delFlag;
        /**
         * 预算控制策略id
         */
    private Long budgetStrategyId;
        /**
         * 项目类型层级，0为父级，1为子级
         */
    private Integer projectTypeLevel;
        /**
         * 父项目类型id
         */
    private Long parentProjectTypeId;
        /**
         * 是否启用，0未启用(失效)，1启用（生效）
         */
    private Integer enableFlag;
        /**
         * 需求组id
         */
    private Long requirementGroupId;
        /**
         * 需求类型id
         */
    private Long requirementTypeId;
}

