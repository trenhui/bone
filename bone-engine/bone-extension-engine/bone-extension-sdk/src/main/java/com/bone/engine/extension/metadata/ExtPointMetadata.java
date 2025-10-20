package com.bone.engine.extension.metadata;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 扩展点元数据模型
 * <p>
 * 用于存储和传输扩展点及其实现的完整元数据信息，支持可视化展示和配置
 * </p>
 * 
 * @since 1.0.0
 */
public class ExtPointMetadata implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // 扩展点基本信息
    private String interfaceName;           // 接口全限定名
    private String interfaceSimpleName;     // 接口简单名称
    private String description;             // 扩展点描述
    private String owner;                   // 负责人
    private String documentationUrl;        // 文档链接
    private boolean deprecated;             // 是否废弃
    private String deprecatedSince;         // 废弃版本
    private String replacement;             // 替代方案
    
    // 扩展点实现信息
    private List<ExtensionImplMetadata> implementations;  // 所有实现
    
    // 方法信息
    private List<ExtPointMethodMetadata> methods;         // 接口方法信息
    
    // 分类和标签
    private String category;                // 扩展点分类
    private List<String> tags;              // 标签列表
    
    // 统计信息
    private int implementationCount;        // 实现数量
    private String lastModifiedTime;        // 最后修改时间
    
    // 构造函数和getter/setter方法
    public ExtPointMetadata() {
    }
    
    public String getInterfaceName() {
        return interfaceName;
    }
    
    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }
    
    public String getInterfaceSimpleName() {
        return interfaceSimpleName;
    }
    
    public void setInterfaceSimpleName(String interfaceSimpleName) {
        this.interfaceSimpleName = interfaceSimpleName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getOwner() {
        return owner;
    }
    
    public void setOwner(String owner) {
        this.owner = owner;
    }
    
    public String getDocumentationUrl() {
        return documentationUrl;
    }
    
    public void setDocumentationUrl(String documentationUrl) {
        this.documentationUrl = documentationUrl;
    }
    
    public boolean isDeprecated() {
        return deprecated;
    }
    
    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }
    
    public String getDeprecatedSince() {
        return deprecatedSince;
    }
    
    public void setDeprecatedSince(String deprecatedSince) {
        this.deprecatedSince = deprecatedSince;
    }
    
    public String getReplacement() {
        return replacement;
    }
    
    public void setReplacement(String replacement) {
        this.replacement = replacement;
    }
    
    public List<ExtensionImplMetadata> getImplementations() {
        return implementations;
    }
    
    public void setImplementations(List<ExtensionImplMetadata> implementations) {
        this.implementations = implementations;
    }
    
    public List<ExtPointMethodMetadata> getMethods() {
        return methods;
    }
    
    public void setMethods(List<ExtPointMethodMetadata> methods) {
        this.methods = methods;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public List<String> getTags() {
        return tags;
    }
    
    public void setTags(List<String> tags) {
        this.tags = tags;
    }
    
    public int getImplementationCount() {
        return implementationCount;
    }
    
    public void setImplementationCount(int implementationCount) {
        this.implementationCount = implementationCount;
    }
    
    public String getLastModifiedTime() {
        return lastModifiedTime;
    }
    
    public void setLastModifiedTime(String lastModifiedTime) {
        this.lastModifiedTime = lastModifiedTime;
    }
}