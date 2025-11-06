package com.bone.procurement.service.security;

import com.bone.procurement.entity.User;
import com.bone.procurement.model.Role;

import java.util.List;
import java.util.Optional;

/**
 * 用户服务接口
 * 提供用户管理、认证和授权相关功能
 */
public interface UserService {
    
    /**
     * 根据ID查找用户
     * @param userId 用户ID
     * @return 用户对象
     */
    User findById(String userId);
    
    /**
     * 根据用户名查找用户
     * @param username 用户名
     * @return 用户对象
     */
    Optional<User> findByUsername(String username);
    
    /**
     * 根据审批级别查找审批人
     * @param approvalLevel 审批级别
     * @return 审批人列表
     */
    List<User> findApproversByLevel(int approvalLevel);
    
    /**
     * 根据角色查找用户
     * @param role 角色
     * @return 用户列表
     */
    List<User> findByRole(Role role);
    
    /**
     * 检查用户是否拥有指定角色
     * @param userId 用户ID
     * @param role 角色
     * @return 是否拥有该角色
     */
    boolean hasRole(String userId, Role role);
    
    /**
     * 检查用户是否拥有指定权限
     * @param userId 用户ID
     * @param permission 权限代码
     * @return 是否拥有该权限
     */
    boolean hasPermission(String userId, String permission);
    
    /**
     * 获取用户拥有的所有角色
     * @param userId 用户ID
     * @return 角色列表
     */
    List<Role> getUserRoles(String userId);
    
    /**
     * 获取用户拥有的所有权限
     * @param userId 用户ID
     * @return 权限代码列表
     */
    List<String> getUserPermissions(String userId);
    
    /**
     * 根据部门查找用户
     * @param departmentId 部门ID
     * @return 用户列表
     */
    List<User> findByDepartment(String departmentId);
    
    /**
     * 查找用户的直接上级
     * @param userId 用户ID
     * @return 直接上级用户
     */
    Optional<User> findDirectSupervisor(String userId);
    
    /**
     * 检查用户是否可以审批指定金额的订单
     * @param userId 用户ID
     * @param orderAmount 订单金额
     * @return 是否可以审批
     */
    boolean canApproveAmount(String userId, double orderAmount);
    
    /**
     * 获取用户的审批限额
     * @param userId 用户ID
     * @return 审批限额
     */
    double getApprovalLimit(String userId);
}