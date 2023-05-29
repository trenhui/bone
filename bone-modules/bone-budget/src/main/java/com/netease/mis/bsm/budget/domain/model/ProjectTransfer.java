package com.netease.mis.bsm.budget.domain.model;

import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;
import java.util.*;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 预算MPC转移项目
 *
 * @author 梅山源码
 */
@Table("budget_mpc_project_transfer")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectTransfer extends TenantAbstractEntity<Long> {
        /**
         * 来源系统
         */
    private String sourceSystem;
        /**
         * MPC需求号
         */
    private String requirementId;
        /**
         * 原项目
         */
    private String originalProjectId;
        /**
         * 新项目
         */
    private String newProjectId;
        /**
         * 删除标志，1删除，0未删除
         */
    private String delFlag;
}

