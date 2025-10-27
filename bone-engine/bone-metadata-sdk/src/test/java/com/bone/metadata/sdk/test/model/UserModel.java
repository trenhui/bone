package com.bone.metadata.sdk.test.model;

/**
 * 用户模型类
 * 用于测试中的用户数据结构
 */
public class UserModel {
    private Long id;
    private String username;
    private String password;
    private String email;
    private Long roleId;
    private Integer age;
    private Integer status;
    
    /**
     * 获取ID
     */
    public Long getId() {
        return id;
    }
    
    /**
     * 设置ID
     */
    public void setId(Long id) {
        this.id = id;
    }
    
    /**
     * 获取用户名
     */
    public String getUsername() {
        return username;
    }
    
    /**
     * 设置用户名
     */
    public void setUsername(String username) {
        this.username = username;
    }
    
    /**
     * 获取密码
     */
    public String getPassword() {
        return password;
    }
    
    /**
     * 设置密码
     */
    public void setPassword(String password) {
        this.password = password;
    }
    
    /**
     * 获取邮箱
     */
    public String getEmail() {
        return email;
    }
    
    /**
     * 设置邮箱
     */
    public void setEmail(String email) {
        this.email = email;
    }
    
    /**
     * 获取角色ID
     */
    public Long getRoleId() {
        return roleId;
    }
    
    /**
     * 设置角色ID
     */
    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }
    
    /**
     * 获取年龄
     */
    public Integer getAge() {
        return age;
    }
    
    /**
     * 设置年龄
     */
    public void setAge(Integer age) {
        this.age = age;
    }
    
    /**
     * 获取状态
     */
    public Integer getStatus() {
        return status;
    }
    
    /**
     * 设置状态
     */
    public void setStatus(Integer status) {
        this.status = status;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        UserModel userModel = (UserModel) o;
        
        if (id != null ? !id.equals(userModel.id) : userModel.id != null) return false;
        if (username != null ? !username.equals(userModel.username) : userModel.username != null) return false;
        if (password != null ? !password.equals(userModel.password) : userModel.password != null) return false;
        if (email != null ? !email.equals(userModel.email) : userModel.email != null) return false;
        if (roleId != null ? !roleId.equals(userModel.roleId) : userModel.roleId != null) return false;
        if (age != null ? !age.equals(userModel.age) : userModel.age != null) return false;
        return status != null ? status.equals(userModel.status) : userModel.status == null;
    }
    
    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (username != null ? username.hashCode() : 0);
        result = 31 * result + (password != null ? password.hashCode() : 0);
        result = 31 * result + (email != null ? email.hashCode() : 0);
        result = 31 * result + (roleId != null ? roleId.hashCode() : 0);
        result = 31 * result + (age != null ? age.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        return result;
    }
    
    @Override
    public String toString() {
        return "UserModel{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", roleId=" + roleId +
                ", age=" + age +
                ", status=" + status +
                '}';
    }
}