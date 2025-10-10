package com.bone.smartmeta.engine.metadata;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 字段级安全元数据模型类
 */
@Getter
@Setter
public class FieldLevelSecurityMetadata {
    
    // 字段API名称
    private String fieldApiName;
    
    // 可读取的角色列表
    private List<String> readableRoles = new ArrayList<>();
    
    // 可编辑的角色列表
    private List<String> editableRoles = new ArrayList<>();
    
    // 是否对管理员可见
    private boolean visibleToAdmin = true;
    
    // 是否可见
    private boolean visible = true;
    
    // 是否可编辑
    private boolean editable = true;
    
    // 可读字段列表
    private List<String> readableFields = new ArrayList<>();
    
    // 可编辑字段列表
    private List<String> editableFields = new ArrayList<>();
    
    /**
     * 获取是否可见
     */
    public boolean isVisible() {
        return this.visible;
    }
    
    /**
     * 获取是否可编辑
     */
    public boolean isEditable() {
        return this.editable;
    }
    
    /**
     * 获取可读字段列表
     */
    public List<String> getReadableFields() {
        return this.readableFields;
    }
    
    /**
     * 获取可编辑字段列表
     */
    public List<String> getEditableFields() {
        return this.editableFields;
    }
    
    /**
     * 获取安全配置信息
     * 返回当前对象本身作为安全配置
     */
    public FieldLevelSecurityMetadata getProfile() {
        return this;
    }
    
    /**
     * 设置安全配置信息
     * 将传入的安全配置属性复制到当前对象
     */
    public void setProfile(FieldLevelSecurityMetadata profile) {
        if (profile != null) {
            // 使用setter方法来设置属性
            this.readableFields = profile.getReadableFields();
            this.editableFields = profile.getEditableFields();
            this.readableRoles = profile.getReadableRoles();
            this.editableRoles = profile.getEditableRoles();
        }
    }
}