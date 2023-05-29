package com.netease.mis.bsm.budget.domain.model;

import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;
import java.util.*;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 项目成员
 *
 * @author 梅山源码
 */
@Table("project_employee")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectEmployee extends TenantAbstractEntity<Long> {
        /**
         * 删除标识:0未删除，1删除
         */
    private String delFlag;
        /**
         * 项目id
         */
    private Long projectId;
        /**
         * 员工id
         */
    private Long employeeId;
}

