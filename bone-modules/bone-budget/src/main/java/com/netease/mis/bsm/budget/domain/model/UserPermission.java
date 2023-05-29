package com.netease.mis.bsm.budget.domain.model;

import com.bone.core.tenant.TenantAbstractEntity;
import lombok.*;
import java.util.*;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 预算用户权限
 *
 * @author 梅山源码
 */
@Table("budget_user_permission")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPermission extends TenantAbstractEntity<Long> {
        /**
         * 来源系统
         */
    private String sourceSystem;
        /**
         * 用户邮箱
         */
    private String email;
        /**
         * 操作
         */
    private String operate;
        /**
         * 启用不标志1启用，0不启用
         */
    private String enableFlag;
        /**
         * 删除标志，1删除，0未删除
         */
    private String delFlag;
}

