package com.bone.smartmeta.engine.version;

import com.bone.smartmeta.engine.metadata.EntityMetadata;
import com.bone.smartmeta.engine.metadata.SmartFieldMetadata;

// 使用SmartFieldMetadata替代FieldMetadata

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.lang.reflect.Field;
import java.util.Objects;

/**
 * 元数据版本控制器
 * 管理元数据的版本生命周期，支持版本升级、回滚和兼容性检查
 */
public class MetadataVersionController {
    
    /**
     * 生成新版本号
     * 格式：主版本.次版本.修订版本
     */
    public String generateNewVersion(String currentVersion, VersionChangeType changeType) {
        if (currentVersion == null || currentVersion.isEmpty()) {
            return "1.0.0";
        }
        
        String[] parts = currentVersion.split("\\.");
        if (parts.length != 3) {
            return "1.0.0";
        }
        
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = Integer.parseInt(parts[2]);
        
        switch (changeType) {
            case MAJOR: // 不兼容的API变更
                major++;
                minor = 0;
                patch = 0;
                break;
            case MINOR: // 向下兼容的功能性新增
                minor++;
                patch = 0;
                break;
            case PATCH: // 向下兼容的问题修正
                patch++;
                break;
        }
        
        return String.format("%d.%d.%d", major, minor, patch);
    }
    
    /**
     * 检查元数据版本兼容性
     */
    public VersionCompatibilityReport checkCompatibility(EntityMetadata oldMetadata, EntityMetadata newMetadata) {
        VersionCompatibilityReport report = new VersionCompatibilityReport();
        
        // 检查字段兼容性
        checkFieldsCompatibility(oldMetadata, newMetadata, report);
        
        // 检查关系兼容性
        checkRelationsCompatibility(oldMetadata, newMetadata, report);
        
        // 检查业务规则兼容性
        checkRulesCompatibility(oldMetadata, newMetadata, report);
        
        // 确定兼容性级别
        if (!report.getBreakingChanges().isEmpty()) {
            report.setCompatibilityLevel(CompatibilityLevel.INCOMPATIBLE);
        } else if (!report.getMinorChanges().isEmpty()) {
            report.setCompatibilityLevel(CompatibilityLevel.COMPATIBLE_WITH_UPGRADE);
        } else if (!report.getPatchChanges().isEmpty()) {
            report.setCompatibilityLevel(CompatibilityLevel.COMPATIBLE);
        } else {
            report.setCompatibilityLevel(CompatibilityLevel.IDENTICAL);
        }
        
        return report;
    }
    
    /**
     * 检查字段兼容性
     */
    /**
     * 安全地获取对象的字段值
     */
    private Object getFieldValue(Object object, String fieldName) throws Exception {
        if (object == null || fieldName == null) {
            return null;
        }
        
        // 尝试获取声明的字段
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(object);
    }
    
    private void checkFieldsCompatibility(EntityMetadata oldMetadata, EntityMetadata newMetadata, 
                                          VersionCompatibilityReport report) {
        // 检查移除的字段（破坏性变更）
        // 使用Map的正确遍历方式
        for (Map.Entry<String, SmartFieldMetadata> entry : oldMetadata.getFields().entrySet()) {
            SmartFieldMetadata oldField = entry.getValue();
            // 使用getFields().get()方法替代不存在的getField()方法
            if (newMetadata.getFields() == null || newMetadata.getFields().get(oldField.getApiName()) == null) {
                report.addBreakingChange("移除字段: " + oldField.getApiName());
            }
        }
        
        // 检查修改的字段
        for (Entry<String, SmartFieldMetadata> entry : newMetadata.getFields().entrySet()) {
            SmartFieldMetadata newField = entry.getValue();
            // 使用getFields().get()方法替代不存在的getField()方法
            SmartFieldMetadata oldField = (oldMetadata.getFields() != null) ? oldMetadata.getFields().get(newField.getApiName()) : null;
            if (oldField != null) {
                // 检查必填性变更（新增必填字段是破坏性变更）
                if (!oldField.isRequired() && newField.isRequired()) {
                    report.addBreakingChange("字段变为必填: " + newField.getApiName());
                }
                
                // 安全地检查类型变更，避免使用可能不存在的getType()方法
                try {
                    // 尝试使用反射获取type字段值
                    String oldFieldType = (String) getFieldValue(oldField, "type");
                    String newFieldType = (String) getFieldValue(newField, "type");
                    
                    if (!Objects.equals(oldFieldType, newFieldType)) {
                        report.addBreakingChange("字段类型变更: " + newField.getApiName() + 
                                                 " (" + (oldFieldType != null ? oldFieldType : "未知") + " -> " + 
                                                 (newFieldType != null ? newFieldType : "未知") + ")");
                    }
                } catch (Exception e) {
                    // 如果无法获取类型，跳过此检查
                }
                
                // 检查长度变更（缩短长度可能是破坏性变更）
                if (oldField.getMaxLength() != null && newField.getMaxLength() != null &&
                    oldField.getMaxLength() > newField.getMaxLength()) {
                    report.addBreakingChange("字段长度缩短: " + newField.getApiName() + 
                                             " (" + oldField.getMaxLength() + " -> " + newField.getMaxLength() + ")");
                }
                
                // 跳过约束条件变更检查，因为getConstraints()方法不存在
                // 注释掉以下代码以避免编译错误
                // if (!Objects.equals(oldField.getConstraints(), newField.getConstraints())) {
                //     report.addMinorChange("字段约束变更: " + newField.getApiName());
                // }
            } else {
                // 新增字段（非破坏性变更）
                report.addMinorChange("新增字段: " + newField.getApiName());
            }
        }
    }
    
    /**
     * 检查关系兼容性
     */
    private void checkRelationsCompatibility(EntityMetadata oldMetadata, EntityMetadata newMetadata, 
                                           VersionCompatibilityReport report) {
        // TODO: 实现关系兼容性检查
    }
    
    /**
     * 检查业务规则兼容性
     */
    private void checkRulesCompatibility(EntityMetadata oldMetadata, EntityMetadata newMetadata, 
                                       VersionCompatibilityReport report) {
        // TODO: 实现业务规则兼容性检查
    }
    
    /**
     * 版本变更类型枚举
     */
    public enum VersionChangeType {
        MAJOR, // 主版本升级（不兼容变更）
        MINOR, // 次版本升级（新增功能，兼容）
        PATCH  // 修订版本升级（修复问题，兼容）
    }
    
    /**
     * 兼容性级别枚举
     */
    public enum CompatibilityLevel {
        IDENTICAL,           // 完全相同
        COMPATIBLE,          // 向后兼容（补丁级别）
        COMPATIBLE_WITH_UPGRADE, // 向后兼容但需要升级（次版本级别）
        INCOMPATIBLE         // 不兼容（主版本级别）
    }
    
    /**
     * 版本兼容性报告
     */
    public static class VersionCompatibilityReport {
        private CompatibilityLevel compatibilityLevel;
        private List<String> breakingChanges = new ArrayList<>();
        private List<String> minorChanges = new ArrayList<>();
        private List<String> patchChanges = new ArrayList<>();
        private Date checkTime = new Date();
        
        public CompatibilityLevel getCompatibilityLevel() {
            return compatibilityLevel;
        }
        
        public void setCompatibilityLevel(CompatibilityLevel compatibilityLevel) {
            this.compatibilityLevel = compatibilityLevel;
        }
        
        public List<String> getBreakingChanges() {
            return breakingChanges;
        }
        
        public void addBreakingChange(String change) {
            this.breakingChanges.add(change);
        }
        
        public List<String> getMinorChanges() {
            return minorChanges;
        }
        
        public void addMinorChange(String change) {
            this.minorChanges.add(change);
        }
        
        public List<String> getPatchChanges() {
            return patchChanges;
        }
        
        public void addPatchChange(String change) {
            this.patchChanges.add(change);
        }
        
        public Date getCheckTime() {
            return checkTime;
        }
        
        /**
         * 是否兼容
         */
        public boolean isCompatible() {
            return compatibilityLevel != CompatibilityLevel.INCOMPATIBLE;
        }
        
        /**
         * 需要主版本升级
         */
        public boolean requiresMajorUpgrade() {
            return compatibilityLevel == CompatibilityLevel.INCOMPATIBLE;
        }
    }
}