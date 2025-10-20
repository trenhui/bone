package org.bone.engine.metadata.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.*;

/**
 * 实体权限元数据模型 - 定义实体级别的权限配置
 * 支持角色权限、用户权限、部门权限等多维度的访问控制
 * 
 * @author Bone Engine Team
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EntityPermissionMetadata {

    // ================ 核心属性 ================
    
    /**
     * 角色权限配置
     */
    private List<RolePermission> rolePermissions;
    
    /**
     * 用户权限配置
     */
    private List<UserPermission> userPermissions;
    
    /**
     * 部门权限配置
     */
    private List<DepartmentPermission> departmentPermissions;
    
    /**
     * 团队权限配置
     */
    private List<TeamPermission> teamPermissions;
    
    /**
     * 数据行权限规则
     */
    private List<DataRowRule> dataRowRules;
    
    /**
     * 权限继承配置
     */
    private PermissionInheritance inheritance;
    
    /**
     * 权限验证模式
     */
    private String validationMode;
    
    /**
     * 公共访问权限
     */
    private String publicAccess;
    
    /**
     * 默认权限
     */
    private String defaultAccess;
    
    // ================ 构造方法与辅助方法 ================
    
    public EntityPermissionMetadata() {
        this.rolePermissions = new ArrayList<>();
        this.userPermissions = new ArrayList<>();
        this.departmentPermissions = new ArrayList<>();
        this.teamPermissions = new ArrayList<>();
        this.dataRowRules = new ArrayList<>();
        this.inheritance = new PermissionInheritance();
        this.validationMode = "STRICT";
        this.publicAccess = "NONE";
        this.defaultAccess = "NONE";
    }
    
    /**
     * 添加角色权限
     */
    public EntityPermissionMetadata addRolePermission(RolePermission permission) {
        if (this.rolePermissions == null) {
            this.rolePermissions = new ArrayList<>();
        }
        this.rolePermissions.add(permission);
        return this;
    }
    
    /**
     * 添加用户权限
     */
    public EntityPermissionMetadata addUserPermission(UserPermission permission) {
        if (this.userPermissions == null) {
            this.userPermissions = new ArrayList<>();
        }
        this.userPermissions.add(permission);
        return this;
    }
    
    /**
     * 添加部门权限
     */
    public EntityPermissionMetadata addDepartmentPermission(DepartmentPermission permission) {
        if (this.departmentPermissions == null) {
            this.departmentPermissions = new ArrayList<>();
        }
        this.departmentPermissions.add(permission);
        return this;
    }
    
    /**
     * 添加团队权限
     */
    public EntityPermissionMetadata addTeamPermission(TeamPermission permission) {
        if (this.teamPermissions == null) {
            this.teamPermissions = new ArrayList<>();
        }
        this.teamPermissions.add(permission);
        return this;
    }
    
    /**
     * 添加数据行规则
     */
    public EntityPermissionMetadata addDataRowRule(DataRowRule rule) {
        if (this.dataRowRules == null) {
            this.dataRowRules = new ArrayList<>();
        }
        this.dataRowRules.add(rule);
        return this;
    }
    
    /**
     * 检查是否允许创建操作
     */
    public boolean isCreateAllowed(String accessLevel) {
        return "ALL".equals(accessLevel) || "CREATE".equals(accessLevel) || 
               "READ_CREATE".equals(accessLevel) || "CREATE_UPDATE".equals(accessLevel);
    }
    
    /**
     * 检查是否允许读取操作
     */
    public boolean isReadAllowed(String accessLevel) {
        return "ALL".equals(accessLevel) || "READ".equals(accessLevel) || 
               "READ_CREATE".equals(accessLevel) || "READ_UPDATE".equals(accessLevel);
    }
    
    /**
     * 检查是否允许更新操作
     */
    public boolean isUpdateAllowed(String accessLevel) {
        return "ALL".equals(accessLevel) || "UPDATE".equals(accessLevel) || 
               "CREATE_UPDATE".equals(accessLevel) || "READ_UPDATE".equals(accessLevel);
    }
    
    /**
     * 检查是否允许删除操作
     */
    public boolean isDeleteAllowed(String accessLevel) {
        return "ALL".equals(accessLevel) || "DELETE".equals(accessLevel);
    }
    
    // ================ 内部类定义 ================
    
    /**
     * 角色权限内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RolePermission {
        
        /**
         * 角色名称
         */
        private String roleName;
        
        /**
         * 访问级别
         */
        private String accessLevel;
        
        /**
         * 条件表达式
         */
        private String conditionExpression;
        
        /**
         * 允许的字段
         */
        private Set<String> allowedFields;
        
        /**
         * 拒绝的字段
         */
        private Set<String> deniedFields;
        
        public RolePermission() {
            this.accessLevel = "NONE";
            this.allowedFields = new HashSet<>();
            this.deniedFields = new HashSet<>();
        }
    }
    
    /**
     * 用户权限内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserPermission {
        
        /**
         * 用户ID
         */
        private String userId;
        
        /**
         * 访问级别
         */
        private String accessLevel;
        
        /**
         * 条件表达式
         */
        private String conditionExpression;
        
        /**
         * 允许的字段
         */
        private Set<String> allowedFields;
        
        /**
         * 拒绝的字段
         */
        private Set<String> deniedFields;
        
        public UserPermission() {
            this.accessLevel = "NONE";
            this.allowedFields = new HashSet<>();
            this.deniedFields = new HashSet<>();
        }
    }
    
    /**
     * 部门权限内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DepartmentPermission {
        
        /**
         * 部门ID
         */
        private String departmentId;
        
        /**
         * 访问级别
         */
        private String accessLevel;
        
        /**
         * 条件表达式
         */
        private String conditionExpression;
        
        /**
         * 允许的字段
         */
        private Set<String> allowedFields;
        
        /**
         * 拒绝的字段
         */
        private Set<String> deniedFields;
        
        /**
         * 是否包含子部门
         */
        private Boolean includeSubDepartments;
        
        public DepartmentPermission() {
            this.accessLevel = "NONE";
            this.allowedFields = new HashSet<>();
            this.deniedFields = new HashSet<>();
            this.includeSubDepartments = Boolean.FALSE;
        }
    }
    
    /**
     * 团队权限内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TeamPermission {
        
        /**
         * 团队ID
         */
        private String teamId;
        
        /**
         * 访问级别
         */
        private String accessLevel;
        
        /**
         * 条件表达式
         */
        private String conditionExpression;
        
        /**
         * 允许的字段
         */
        private Set<String> allowedFields;
        
        /**
         * 拒绝的字段
         */
        private Set<String> deniedFields;
        
        public TeamPermission() {
            this.accessLevel = "NONE";
            this.allowedFields = new HashSet<>();
            this.deniedFields = new HashSet<>();
        }
    }
    
    /**
     * 数据行规则内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DataRowRule {
        
        /**
         * 规则名称
         */
        private String name;
        
        /**
         * 规则类型
         */
        private String type;
        
        /**
         * 过滤表达式
         */
        private String filterExpression;
        
        /**
         * 目标角色
         */
        private List<String> targetRoles;
        
        /**
         * 目标用户
         */
        private List<String> targetUsers;
        
        /**
         * 优先级
         */
        private Integer priority;
        
        public DataRowRule() {
            this.type = "FILTER";
            this.targetRoles = new ArrayList<>();
            this.targetUsers = new ArrayList<>();
            this.priority = 50;
        }
    }
    
    /**
     * 权限继承内部类
     */
    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PermissionInheritance {
        
        /**
         * 是否继承父实体权限
         */
        private Boolean inheritFromParent;
        
        /**
         * 是否跨实体继承
         */
        private Boolean crossEntityInheritance;
        
        /**
         * 继承规则
         */
        private List<String> inheritanceRules;
        
        public PermissionInheritance() {
            this.inheritFromParent = Boolean.FALSE;
            this.crossEntityInheritance = Boolean.FALSE;
            this.inheritanceRules = new ArrayList<>();
        }
    }
}