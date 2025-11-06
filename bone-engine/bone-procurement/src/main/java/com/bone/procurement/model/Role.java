package com.bone.procurement.model;

/**
 * 角色枚举
 * 定义系统中的用户角色
 */
public enum Role {
    
    /**
     * 普通用户
     * 可以创建采购订单的基本用户
     */
    USER("普通用户", "ROLE_USER", "可以创建和提交采购订单"),
    
    /**
     * 部门经理
     * 可以审批一级订单的角色
     */
    DEPARTMENT_MANAGER("部门经理", "ROLE_DEPARTMENT_MANAGER", "可以审批一级采购订单"),
    
    /**
     * 总监
     * 可以审批二级订单的角色
     */
    DIRECTOR("总监", "ROLE_DIRECTOR", "可以审批二级采购订单"),
    
    /**
     * 副总经理
     * 可以审批三级订单的角色
     */
    DEPUTY_GENERAL_MANAGER("副总经理", "ROLE_DEPUTY_GM", "可以审批三级采购订单"),
    
    /**
     * 总经理
     * 可以审批四级订单的角色，具有最高审批权限
     */
    GENERAL_MANAGER("总经理", "ROLE_GENERAL_MANAGER", "可以审批四级采购订单，具有最高审批权限"),
    
    /**
     * 采购专员
     * 负责执行采购订单的角色
     */
    PURCHASING_SPECIALIST("采购专员", "ROLE_PURCHASING_SPECIALIST", "负责执行采购订单"),
    
    /**
     * 财务人员
     * 负责财务管理相关功能的角色
     */
    FINANCE_STAFF("财务人员", "ROLE_FINANCE_STAFF", "负责采购订单的财务管理"),
    
    /**
     * 系统管理员
     * 系统管理和维护的角色
     */
    ADMINISTRATOR("系统管理员", "ROLE_ADMIN", "系统管理和维护，具有最高系统权限"),
    
    /**
     * 审计人员
     * 负责审计功能的角色
     */
    AUDITOR("审计人员", "ROLE_AUDITOR", "负责系统审计功能"),
    
    /**
     * 供应商管理专员
     * 负责供应商管理的角色
     */
    SUPPLIER_MANAGER("供应商管理专员", "ROLE_SUPPLIER_MANAGER", "负责供应商管理");
    
    private final String displayName;
    private final String roleCode;
    private final String description;
    
    Role(String displayName, String roleCode, String description) {
        this.displayName = displayName;
        this.roleCode = roleCode;
        this.description = description;
    }
    
    /**
     * 获取角色的显示名称
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 获取角色代码
     */
    public String getRoleCode() {
        return roleCode;
    }
    
    /**
     * 获取角色描述
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据角色代码查找角色枚举
     */
    public static Role fromRoleCode(String roleCode) {
        for (Role role : values()) {
            if (role.roleCode.equalsIgnoreCase(roleCode)) {
                return role;
            }
        }
        throw new IllegalArgumentException("No enum constant with role code: " + roleCode);
    }
    
    /**
     * 根据显示名称查找角色枚举
     */
    public static Role fromDisplayName(String displayName) {
        for (Role role : values()) {
            if (role.displayName.equalsIgnoreCase(displayName)) {
                return role;
            }
        }
        throw new IllegalArgumentException("No enum constant with display name: " + displayName);
    }
    
    /**
     * 检查角色是否为审批角色
     */
    public boolean isApprovalRole() {
        return this == DEPARTMENT_MANAGER || 
               this == DIRECTOR || 
               this == DEPUTY_GENERAL_MANAGER || 
               this == GENERAL_MANAGER;
    }
    
    /**
     * 获取角色对应的审批级别
     */
    public ApprovalLevel getApprovalLevel() {
        switch (this) {
            case DEPARTMENT_MANAGER:
                return ApprovalLevel.LEVEL_1;
            case DIRECTOR:
                return ApprovalLevel.LEVEL_2;
            case DEPUTY_GENERAL_MANAGER:
                return ApprovalLevel.LEVEL_3;
            case GENERAL_MANAGER:
                return ApprovalLevel.LEVEL_4;
            default:
                return null;
        }
    }
    
    /**
     * 检查角色是否为管理员角色
     */
    public boolean isAdminRole() {
        return this == ADMINISTRATOR;
    }
}