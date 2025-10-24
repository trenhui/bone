package org.bone.engine.metadata.config;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.bone.engine.metadata.model.*;
import org.bone.engine.metadata.processor.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 元数据处理器配置类 - 提供各类元数据组件的处理器配置
 * 
 * @author Bone Engine Team
 */
@Configuration
public class MetadataProcessorConfig {
    
    /**
     * 注册实体元数据处理器
     */
    @Bean
    @ConditionalOnMissingBean(EntityMetadataProcessor.class)
    public EntityMetadataProcessor entityMetadataProcessor() {
        return new DefaultEntityMetadataProcessor();
    }
    
    /**
     * 注册字段元数据处理器
     */
    @Bean
    @ConditionalOnMissingBean(FieldMetadataProcessor.class)
    public FieldMetadataProcessor fieldMetadataProcessor() {
        return new DefaultFieldMetadataProcessor();
    }
    
    /**
     * 注册关系元数据处理器
     */
    @Bean
    @ConditionalOnMissingBean(RelationshipMetadataProcessor.class)
    public RelationshipMetadataProcessor relationshipMetadataProcessor() {
        return new DefaultRelationshipMetadataProcessor();
    }
    
    /**
     * 注册业务规则元数据处理器
     */
    @Bean
    @ConditionalOnMissingBean(BusinessRuleMetadataProcessor.class)
    public BusinessRuleMetadataProcessor businessRuleMetadataProcessor() {
        return new DefaultBusinessRuleMetadataProcessor();
    }
    
    /**
     * 注册操作元数据处理器
     */
    @Bean
    @ConditionalOnMissingBean(OperationMetadataProcessor.class)
    public OperationMetadataProcessor operationMetadataProcessor() {
        return new DefaultOperationMetadataProcessor();
    }
    
    /**
     * 注册流程元数据处理器
     */
    @Bean
    @ConditionalOnMissingBean(ProcessMetadataProcessor.class)
    public ProcessMetadataProcessor processMetadataProcessor() {
        return new DefaultProcessMetadataProcessor();
    }
    
    /**
     * 注册索引元数据处理器
     */
    @Bean
    @ConditionalOnMissingBean(IndexMetadataProcessor.class)
    public IndexMetadataProcessor indexMetadataProcessor() {
        return new DefaultIndexMetadataProcessor();
    }
    
    /**
     * 注册AI增强处理器
     */
    @Bean
    @ConditionalOnMissingBean(AIEnhancementProcessor.class)
    public AIEnhancementProcessor aiEnhancementProcessor() {
        return new DefaultAIEnhancementProcessor();
    }
    
    /**
     * 注册MCP接口处理器
     */
    @Bean
    @ConditionalOnMissingBean(MCPInterfaceProcessor.class)
    public MCPInterfaceProcessor mcpInterfaceProcessor() {
        return new DefaultMCPInterfaceProcessor();
    }
    
    /**
     * 注册元数据转换处理器
     */
    @Bean
    @ConditionalOnMissingBean(MetadataTransformationProcessor.class)
    public MetadataTransformationProcessor metadataTransformationProcessor() {
        return new DefaultMetadataTransformationProcessor();
    }
    
    /**
     * 注册元数据导入导出处理器
     */
    @Bean
    @ConditionalOnMissingBean(MetadataImportExportProcessor.class)
    public MetadataImportExportProcessor metadataImportExportProcessor() {
        return new DefaultMetadataImportExportProcessor();
    }
    
    /**
     * 注册元数据生成器
     */
    @Bean
    @ConditionalOnMissingBean(MetadataGenerator.class)
    public MetadataGenerator metadataGenerator() {
        return new DefaultMetadataGenerator();
    }
    
    // ========== 处理器接口定义 ==========
    
    /**
     * 实体元数据处理器接口
     */
    public interface EntityMetadataProcessor {
        void process(EntityMetadata metadata) throws ProcessingException;
    }
    
    /**
     * 字段元数据处理器接口
     */
    public interface FieldMetadataProcessor {
        void process(FieldMetadata metadata) throws ProcessingException;
    }
    
    /**
     * 关系元数据处理器接口
     */
    public interface RelationshipMetadataProcessor {
        void process(RelationshipMetadata metadata) throws ProcessingException;
    }
    
    /**
     * 业务规则元数据处理器接口
     */
    public interface BusinessRuleMetadataProcessor {
        void process(BusinessRuleMetadata metadata) throws ProcessingException;
    }
    
    /**
     * 操作元数据处理器接口
     */
    public interface OperationMetadataProcessor {
        void process(OperationMetadata metadata) throws ProcessingException;
    }
    
    /**
     * 流程元数据处理器接口
     */
    public interface ProcessMetadataProcessor {
        void process(ProcessMetadata metadata) throws ProcessingException;
    }
    
    /**
     * 索引元数据处理器接口
     */
    public interface IndexMetadataProcessor {
        void process(IndexMetadata metadata) throws ProcessingException;
    }
    
    /**
     * AI增强处理器接口
     */
    public interface AIEnhancementProcessor {
        void process(AIEnhancement metadata) throws ProcessingException;
    }
    
    /**
     * MCP接口处理器接口
     */
    public interface MCPInterfaceProcessor {
        void process(MCPInterface metadata) throws ProcessingException;
    }
    
    /**
     * 元数据转换处理器接口
     */
    public interface MetadataTransformationProcessor {
        EntityMetadata transform(EntityMetadata source, TransformationOptions options) throws ProcessingException;
    }
    
    /**
     * 元数据导入导出处理器接口
     */
    public interface MetadataImportExportProcessor {
        String export(EntityMetadata metadata, String format) throws ProcessingException;
        EntityMetadata importMetadata(String data, String format) throws ProcessingException;
    }
    
    /**
     * 元数据生成器接口
     */
    public interface MetadataGenerator {
        EntityMetadata generateFromTemplate(String templateName, TemplateOptions options) throws ProcessingException;
        FieldMetadata generateField(String fieldName, FieldType fieldType) throws ProcessingException;
    }
    
    // ========== 处理异常类 ==========
    
    /**
     * 处理异常类
     */
    public static class ProcessingException extends Exception {
        private ProcessingType processingType;
        private String processingComponent;
        
        public ProcessingException(String message) {
            super(message);
        }
        
        public ProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
        
        public ProcessingException(String message, ProcessingType processingType) {
            super(message);
            this.processingType = processingType;
        }
        
        public ProcessingException(String message, ProcessingType processingType, String processingComponent) {
            super(message);
            this.processingType = processingType;
            this.processingComponent = processingComponent;
        }
        
        public ProcessingType getProcessingType() {
            return processingType;
        }
        
        public String getProcessingComponent() {
            return processingComponent;
        }
        
        public enum ProcessingType {
            VALIDATION, TRANSFORMATION, GENERATION, IMPORT_EXPORT, PERSISTENCE, RENDERING
        }
    }
    
    // ========== 转换选项类 ==========
    
    /**
     * 转换选项类
     */
    public static class TransformationOptions {
        private boolean includeFields = true;
        private boolean includeRelationships = true;
        private boolean includeBusinessRules = true;
        private boolean includeOperations = true;
        private boolean includeProcesses = true;
        private boolean includeIndexes = true;
        private boolean includePermissions = true;
        private boolean includeAIExtensions = false;
        private boolean includeKGExtensions = false;
        private boolean includeMCPExtensions = false;
        
        public boolean isIncludeFields() {
            return includeFields;
        }
        public void setIncludeFields(boolean includeFields) {
            this.includeFields = includeFields;
        }
        public boolean isIncludeRelationships() {
            return includeRelationships;
        }
        public void setIncludeRelationships(boolean includeRelationships) {
            this.includeRelationships = includeRelationships;
        }
        public boolean isIncludeBusinessRules() {
            return includeBusinessRules;
        }
        public void setIncludeBusinessRules(boolean includeBusinessRules) {
            this.includeBusinessRules = includeBusinessRules;
        }
        public boolean isIncludeOperations() {
            return includeOperations;
        }
        public void setIncludeOperations(boolean includeOperations) {
            this.includeOperations = includeOperations;
        }
        public boolean isIncludeProcesses() {
            return includeProcesses;
        }
        public void setIncludeProcesses(boolean includeProcesses) {
            this.includeProcesses = includeProcesses;
        }
        public boolean isIncludeIndexes() {
            return includeIndexes;
        }
        public void setIncludeIndexes(boolean includeIndexes) {
            this.includeIndexes = includeIndexes;
        }
        public boolean isIncludePermissions() {
            return includePermissions;
        }
        public void setIncludePermissions(boolean includePermissions) {
            this.includePermissions = includePermissions;
        }
        public boolean isIncludeAIExtensions() {
            return includeAIExtensions;
        }
        public void setIncludeAIExtensions(boolean includeAIExtensions) {
            this.includeAIExtensions = includeAIExtensions;
        }
        public boolean isIncludeKGExtensions() {
            return includeKGExtensions;
        }
        public void setIncludeKGExtensions(boolean includeKGExtensions) {
            this.includeKGExtensions = includeKGExtensions;
        }
        public boolean isIncludeMCPExtensions() {
            return includeMCPExtensions;
        }
        public void setIncludeMCPExtensions(boolean includeMCPExtensions) {
            this.includeMCPExtensions = includeMCPExtensions;
        }
    }
    
    /**
     * 字段类型枚举
     */
    public enum FieldType {
        TEXT, STRING, NUMBER, INTEGER, LONG, DOUBLE, BOOLEAN, DATE, DATETIME, PICKLIST, LOOKUP, CURRENCY, FILE, JSON
    }
    
    /**
     * 模板选项类
     */
    public static class TemplateOptions {
        private String baseEntity;
        private String domain;
        private String version;
        private boolean addAuditFields = true;
        private boolean addCommonFields = true;
        private boolean addSecurityFields = true;
        
        public String getBaseEntity() {
            return baseEntity;
        }
        public void setBaseEntity(String baseEntity) {
            this.baseEntity = baseEntity;
        }
        public String getDomain() {
            return domain;
        }
        public void setDomain(String domain) {
            this.domain = domain;
        }
        public String getVersion() {
            return version;
        }
        public void setVersion(String version) {
            this.version = version;
        }
        public boolean isAddAuditFields() {
            return addAuditFields;
        }
        public void setAddAuditFields(boolean addAuditFields) {
            this.addAuditFields = addAuditFields;
        }
        public boolean isAddCommonFields() {
            return addCommonFields;
        }
        public void setAddCommonFields(boolean addCommonFields) {
            this.addCommonFields = addCommonFields;
        }
        public boolean isAddSecurityFields() {
            return addSecurityFields;
        }
        public void setAddSecurityFields(boolean addSecurityFields) {
            this.addSecurityFields = addSecurityFields;
        }
    }
    
    // ========== 默认处理器实现 ==========
    
    /**
     * 默认实体元数据处理器实现
     */
    public static class DefaultEntityMetadataProcessor implements EntityMetadataProcessor {
        
        @Override
        public void process(EntityMetadata metadata) throws ProcessingException {
            // 实体元数据处理逻辑
            try {
                // 处理审计信息
                if (metadata.getCreatedBy() == null) {
                    metadata.setCreatedBy("system");
                }
                // 设置创建时间和最后修改时间 - 注释掉因为EntityMetadata类没有这些方法
                // if (metadata.getCreatedTime() == null) {
                //     metadata.setCreatedTime(System.currentTimeMillis());
                // }
                // metadata.setLastModifiedTime(System.currentTimeMillis());
                
                // 处理版本信息
                if (metadata.getVersion() == null) {
                    metadata.setVersion("1.0.0");
                }
                
                // 处理子实体关系
                if ("SUB_ENTITY".equals(metadata.getEntityType()) && metadata.getParentEntity() != null) {
                    // 添加对子实体的处理
                }
            } catch (Exception e) {
                throw new ProcessingException("实体元数据处理失败: " + e.getMessage(), 
                        ProcessingException.ProcessingType.PERSISTENCE, "EntityMetadata");
            }
        }
    }
    
    /**
     * 默认字段元数据处理器实现
     */
    public static class DefaultFieldMetadataProcessor implements FieldMetadataProcessor {
        
        @Override
        public void process(FieldMetadata metadata) throws ProcessingException {
            // 字段元数据处理逻辑
            try {
                // 处理默认值 - 注释掉因为FieldMetadata类没有isRequired方法
                // if (metadata.getDefaultValue() == null && !metadata.isRequired() && "BOOLEAN".equals(metadata.getType())) {
                //     metadata.setDefaultValue(false);
                // }
                
                // 处理计算字段 - 注释掉因为FieldMetadata类没有isCalculated方法
                // if (metadata.isCalculated() && metadata.getCalculationExpression() == null) {
                //     throw new ProcessingException("计算字段必须定义计算表达式", 
                //             ProcessingException.ProcessingType.VALIDATION, "FieldMetadata");
                // }
                
                // 处理加密字段 - 注释掉因为FieldMetadata类没有isEncrypted方法
                // if (metadata.isEncrypted() && (metadata.getType() == null || !metadata.getType().equals("TEXT"))) {
                //     throw new ProcessingException("只有文本类型字段可以加密", 
                //             ProcessingException.ProcessingType.VALIDATION, "FieldMetadata");
                // }
            } catch (Exception e) {
                throw new ProcessingException("字段元数据处理失败: " + e.getMessage(), 
                        ProcessingException.ProcessingType.PERSISTENCE, "FieldMetadata");
            }
        }
    }
    
    /**
     * 默认关系元数据处理器实现
     */
    public static class DefaultRelationshipMetadataProcessor implements RelationshipMetadataProcessor {
        
        @Override
        public void process(RelationshipMetadata metadata) throws ProcessingException {
            // 关系元数据处理逻辑
            try {
                // 设置默认级联操作
                if (metadata.getCascade() == null && "ONE_TO_MANY".equals(metadata.getType())) {
                    metadata.setCascade("PERSIST");
                }
                
                // 设置默认批处理大小
                if (metadata.getBatchSize() == null) {
                    metadata.setBatchSize(20);
                }
                
                // 处理关系字段映射
                if (metadata.getSourceField() == null && "MANY_TO_ONE".equals(metadata.getType())) {
                    metadata.setSourceField(metadata.getTargetEntity().toLowerCase() + "Id");
                }
            } catch (Exception e) {
                throw new ProcessingException("关系元数据处理失败: " + e.getMessage(), 
                        ProcessingException.ProcessingType.PERSISTENCE, "RelationshipMetadata");
            }
        }
    }
    
    /**
     * 默认业务规则元数据处理器实现
     */
    public static class DefaultBusinessRuleMetadataProcessor implements BusinessRuleMetadataProcessor {
        
        @Override
        public void process(BusinessRuleMetadata metadata) throws ProcessingException {
            // 业务规则元数据处理逻辑
            try {
                // 设置默认规则类型
                if (metadata.getRuleType() == null) {
                    metadata.setRuleType("VALIDATION");
                }
                
                // 设置默认优先级
                if (metadata.getPriority() == null) {
                    metadata.setPriority(100); // 默认中等优先级
                }
                
                // 设置默认严重程度
                if (metadata.getSeverity() == null) {
                    metadata.setSeverity("ERROR");
                }
                
                // 处理触发事件 - 修复类型不兼容问题，使用Arrays.asList()转换为List
                if (metadata.getTriggerEvents() == null) {
                    metadata.setTriggerEvents(Arrays.asList("BEFORE_CREATE", "BEFORE_UPDATE"));
                }
            } catch (Exception e) {
                throw new ProcessingException("业务规则元数据处理失败: " + e.getMessage(), 
                        ProcessingException.ProcessingType.PERSISTENCE, "BusinessRuleMetadata");
            }
        }
    }
    
    /**
     * 默认操作元数据处理器实现
     */
    public static class DefaultOperationMetadataProcessor implements OperationMetadataProcessor {
        
        @Override
        public void process(OperationMetadata metadata) throws ProcessingException {
            // 操作元数据处理逻辑
            try {
                // 设置默认操作类型
                if (metadata.getType() == null) {
                    metadata.setType("CUSTOM");
                }
                
                // 设置默认权限 - 注释掉因为OperationMetadata类没有这些方法
                // if (metadata.getRequiredPermissions() == null) {
                //     metadata.setRequiredPermissions(new String[]{"OPERATE_" + metadata.getName().toUpperCase()});
                // }
                
                // 设置默认事务隔离级别 - 注释掉因为OperationMetadata类没有这个方法
                // if (metadata.getTransactionIsolation() == null) {
                //     metadata.setTransactionIsolation("READ_COMMITTED");
                // }
            } catch (Exception e) {
                throw new ProcessingException("操作元数据处理失败: " + e.getMessage(), 
                        ProcessingException.ProcessingType.PERSISTENCE, "OperationMetadata");
            }
        }
    }
    
    // 其他处理器实现类似，为简洁起见省略...
    
    /**
     * 默认流程元数据处理器实现
     */
    public static class DefaultProcessMetadataProcessor implements ProcessMetadataProcessor {
        
        @Override
        public void process(ProcessMetadata metadata) throws ProcessingException {
            // 流程元数据处理逻辑
            try {
                // 设置默认流程类型
                if (metadata.getType() == null) {
                    metadata.setType("SEQUENTIAL");
                }
                
                // 设置默认事务性 - 注释掉因为ProcessMetadata类没有isTransactional方法
                // if (metadata.isTransactional() == null) {
                //     metadata.setTransactional(true);
                // }
            } catch (Exception e) {
                throw new ProcessingException("流程元数据处理失败: " + e.getMessage(), 
                        ProcessingException.ProcessingType.PERSISTENCE, "ProcessMetadata");
            }
        }
    }
    
    /**
     * 默认索引元数据处理器实现
     */
    public static class DefaultIndexMetadataProcessor implements IndexMetadataProcessor {
        
        @Override
        public void process(IndexMetadata metadata) throws ProcessingException {
            // 索引元数据处理逻辑
            try {
                // 设置默认索引类型
                if (metadata.getType() == null) {
                    metadata.setType("INDEX");
                }
                
                // 验证索引字段
                if (metadata.getFields() == null || metadata.getFields().isEmpty()) {
                    throw new ProcessingException("索引必须包含至少一个字段", 
                            ProcessingException.ProcessingType.VALIDATION, "IndexMetadata");
                }
            } catch (ProcessingException e) {
                throw e;
            } catch (Exception e) {
                throw new ProcessingException("索引元数据处理失败: " + e.getMessage(), 
                        ProcessingException.ProcessingType.PERSISTENCE, "IndexMetadata");
            }
        }
    }
    
    /**
     * 默认AI增强处理器实现
     */
    public static class DefaultAIEnhancementProcessor implements AIEnhancementProcessor {
        
        @Override
        public void process(AIEnhancement metadata) throws ProcessingException {
            // AI增强处理逻辑
            try {
                // 设置默认置信度阈值 - 注释掉因为AIEnhancement类没有这些方法
                // if (metadata.getTaggingConfidenceThreshold() == null) {
                //     metadata.setTaggingConfidenceThreshold(0.7);
                // }
            } catch (Exception e) {
                throw new ProcessingException("AI增强配置处理失败: " + e.getMessage(), 
                        ProcessingException.ProcessingType.PERSISTENCE, "AIEnhancement");
            }
        }
    }
    
    /**
     * 默认MCP接口处理器实现
     */
    public static class DefaultMCPInterfaceProcessor implements MCPInterfaceProcessor {
        
        @Override
        public void process(MCPInterface metadata) throws ProcessingException {
            // MCP接口处理逻辑
            try {
                // 处理MCP接口配置
                if (metadata.getDataIngestion() == null) {
                    metadata.setDataIngestion("/api/mcp/ingest");
                }
                if (metadata.getMetadataTagging() == null) {
                    metadata.setMetadataTagging("/api/mcp/tagging");
                }
                if (metadata.getInteroperability() == null) {
                    metadata.setInteroperability("/api/mcp/interop");
                }
            } catch (Exception e) {
                throw new ProcessingException("MCP接口配置处理失败: " + e.getMessage(), 
                        ProcessingException.ProcessingType.PERSISTENCE, "MCPInterface");
            }
        }
    }
    
    /**
     * 默认元数据转换处理器实现
     */
    public static class DefaultMetadataTransformationProcessor implements MetadataTransformationProcessor {
        
        @Override
        public EntityMetadata transform(EntityMetadata source, TransformationOptions options) throws ProcessingException {
            // 创建新的实体元数据副本
            EntityMetadata target = new EntityMetadata();
            
            // 复制基本信息
            target.setApiName(source.getApiName() + "_transformed");
            target.setLabel(source.getLabel() + " (转换版)");
            target.setDescription(source.getDescription());
            target.setDomain(source.getDomain());
            target.setEntityType(source.getEntityType());
            target.setVersion(source.getVersion());
            
            // 根据选项复制组件
            if (options.isIncludeFields()) {
                target.setFields(source.getFields());
            }
            if (options.isIncludeRelationships()) {
                target.setRelationships(source.getRelationships());
            }
            if (options.isIncludeBusinessRules()) {
                target.setBusinessRules(source.getBusinessRules());
            }
            if (options.isIncludeOperations()) {
                target.setOperations(source.getOperations());
            }
            if (options.isIncludeProcesses()) {
                target.setProcesses(source.getProcesses());
            }
            if (options.isIncludeIndexes()) {
                target.setIndexes(source.getIndexes());
            }
            if (options.isIncludePermissions()) {
                target.setPermissions(source.getPermissions());
            }
            if (options.isIncludeAIExtensions()) {
                target.setAiEnhancement(source.getAiEnhancement());
            }
            if (options.isIncludeKGExtensions()) {
                target.setKgConfig(source.getKgConfig());
            }
            if (options.isIncludeMCPExtensions()) {
                target.setMcpInterface(source.getMcpInterface());
            }
            
            return target;
        }
    }
    
    /**
     * 默认元数据导入导出处理器实现
     */
    public static class DefaultMetadataImportExportProcessor implements MetadataImportExportProcessor {
        
        @Override
        public String export(EntityMetadata metadata, String format) throws ProcessingException {
            // 这里简化实现，实际应根据format参数导出不同格式
            if (!"JSON".equalsIgnoreCase(format)) {
                throw new ProcessingException("不支持的导出格式: " + format, 
                        ProcessingException.ProcessingType.IMPORT_EXPORT, "Export");
            }
            
            // 实际实现应该使用JSON序列化库
            return "{\"exported\": true, \"entity\": \"" + metadata.getApiName() + "\"}";
        }
        
        @Override
        public EntityMetadata importMetadata(String data, String format) throws ProcessingException {
            // 这里简化实现，实际应根据format参数导入不同格式
            if (!"JSON".equalsIgnoreCase(format)) {
                throw new ProcessingException("不支持的导入格式: " + format, 
                        ProcessingException.ProcessingType.IMPORT_EXPORT, "Import");
            }
            
            // 实际实现应该使用JSON反序列化库
            EntityMetadata metadata = new EntityMetadata();
            metadata.setApiName("imported_entity");
            metadata.setLabel("导入的实体");
            return metadata;
        }
    }
    
    /**
     * 默认元数据生成器实现
     */
    public static class DefaultMetadataGenerator implements MetadataGenerator {
        
        @Override
        public EntityMetadata generateFromTemplate(String templateName, TemplateOptions options) throws ProcessingException {
            EntityMetadata metadata = new EntityMetadata();
            metadata.setApiName(templateName);
            metadata.setLabel(templateName + " Entity");
            metadata.setDomain(options.getDomain() != null ? options.getDomain() : "COMMON");
            metadata.setEntityType("STANDARD");
            metadata.setVersion(options.getVersion() != null ? options.getVersion() : "1.0.0");
            
            // 添加审计字段
            if (options.isAddAuditFields()) {
                // 实际实现应该添加createdBy, createdTime, lastModifiedBy, lastModifiedTime等字段
            }
            
            return metadata;
        }
        
        @Override
        public FieldMetadata generateField(String fieldName, FieldType fieldType) throws ProcessingException {
            FieldMetadata field = new FieldMetadata();
            field.setName(fieldName);
            field.setLabel(fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1));
            
            // 根据fieldType设置对应的字段类型
            switch (fieldType) {
                case TEXT: 
                case STRING: 
                    field.setType("TEXT");
                    field.setMaxLength(255);
                    break;
                case NUMBER: 
                case INTEGER: 
                case LONG: 
                    field.setType("NUMBER");
                    break;
                case DOUBLE: 
                    field.setType("NUMBER");
                    break;
                case BOOLEAN: 
                    field.setType("BOOLEAN");
                    break;
                case DATE: 
                    field.setType("DATE");
                    break;
                case DATETIME: 
                    field.setType("DATE");
                    break;
                case PICKLIST: 
                    field.setType("PICKLIST");
                    break;
                case LOOKUP: 
                    field.setType("LOOKUP");
                    break;
                case CURRENCY: 
                    field.setType("CURRENCY");
                    break;
                case FILE: 
                    field.setType("TEXT");
                    break;
                case JSON: 
                    field.setType("JSON");
                    break;
                default: 
                    throw new ProcessingException("不支持的字段类型: " + fieldType, 
                            ProcessingException.ProcessingType.GENERATION, "Field");
            }
            
            return field;
        }
    }
}