package org.bone.engine.metadata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 元数据配置属性类 - 用于定义元数据服务的配置参数
 * 
 * @author Bone Engine Team
 */
@ConfigurationProperties(prefix = "bone.metadata")
public class MetadataProperties {
    
    /**
     * 默认业务域
     */
    private String defaultDomain = "COMMON";
    
    /**
     * 是否启用版本控制
     */
    private boolean versionControlEnabled = true;
    
    /**
     * 是否启用元数据验证
     */
    private boolean validationEnabled = true;
    
    /**
     * 是否启用缓存
     */
    private boolean cacheEnabled = true;
    
    /**
     * 缓存过期时间（毫秒）
     */
    private long cacheTTL = 3600000; // 默认1小时
    
    /**
     * 缓存最大容量
     */
    private int cacheMaxSize = 1000;
    
    /**
     * 序列化格式
     */
    private String serializationFormat = "JSON";
    
    /**
     * 是否启用事件通知
     */
    private boolean eventNotificationEnabled = true;
    
    /**
     * 是否启用AI增强功能
     */
    private boolean aiEnhancementEnabled = false;
    
    /**
     * 是否启用知识图谱集成
     */
    private boolean knowledgeGraphEnabled = false;
    
    /**
     * 是否启用MCP互操作协议
     */
    private boolean mcpProtocolEnabled = false;
    
    /**
     * AI服务配置
     */
    private AIServiceConfig aiService = new AIServiceConfig();
    
    /**
     * 知识图谱配置
     */
    private KnowledgeGraphConfig kgConfig = new KnowledgeGraphConfig();
    
    /**
     * MCP协议配置
     */
    private MCPProtocolConfig mcpConfig = new MCPProtocolConfig();
    
    /**
     * 实体元数据配置
     */
    private EntityConfig entityConfig = new EntityConfig();
    
    /**
     * 安全配置
     */
    private SecurityConfig securityConfig = new SecurityConfig();
    
    /**
     * 数据库配置
     */
    private DatabaseConfig databaseConfig = new DatabaseConfig();
    
    /**
     * 导入导出配置
     */
    private ImportExportConfig importExportConfig = new ImportExportConfig();
    
    // ========== Getters and Setters ==========
    
    public String getDefaultDomain() {
        return defaultDomain;
    }
    
    public void setDefaultDomain(String defaultDomain) {
        this.defaultDomain = defaultDomain;
    }
    
    public boolean isVersionControlEnabled() {
        return versionControlEnabled;
    }
    
    public void setVersionControlEnabled(boolean versionControlEnabled) {
        this.versionControlEnabled = versionControlEnabled;
    }
    
    public boolean isValidationEnabled() {
        return validationEnabled;
    }
    
    public void setValidationEnabled(boolean validationEnabled) {
        this.validationEnabled = validationEnabled;
    }
    
    public boolean isCacheEnabled() {
        return cacheEnabled;
    }
    
    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }
    
    public long getCacheTTL() {
        return cacheTTL;
    }
    
    public void setCacheTTL(long cacheTTL) {
        this.cacheTTL = cacheTTL;
    }
    
    public int getCacheMaxSize() {
        return cacheMaxSize;
    }
    
    public void setCacheMaxSize(int cacheMaxSize) {
        this.cacheMaxSize = cacheMaxSize;
    }
    
    public String getSerializationFormat() {
        return serializationFormat;
    }
    
    public void setSerializationFormat(String serializationFormat) {
        this.serializationFormat = serializationFormat;
    }
    
    public boolean isEventNotificationEnabled() {
        return eventNotificationEnabled;
    }
    
    public void setEventNotificationEnabled(boolean eventNotificationEnabled) {
        this.eventNotificationEnabled = eventNotificationEnabled;
    }
    
    public boolean isAiEnhancementEnabled() {
        return aiEnhancementEnabled;
    }
    
    public void setAiEnhancementEnabled(boolean aiEnhancementEnabled) {
        this.aiEnhancementEnabled = aiEnhancementEnabled;
    }
    
    public boolean isKnowledgeGraphEnabled() {
        return knowledgeGraphEnabled;
    }
    
    public void setKnowledgeGraphEnabled(boolean knowledgeGraphEnabled) {
        this.knowledgeGraphEnabled = knowledgeGraphEnabled;
    }
    
    public boolean isMcpProtocolEnabled() {
        return mcpProtocolEnabled;
    }
    
    public void setMcpProtocolEnabled(boolean mcpProtocolEnabled) {
        this.mcpProtocolEnabled = mcpProtocolEnabled;
    }
    
    public AIServiceConfig getAiService() {
        return aiService;
    }
    
    public void setAiService(AIServiceConfig aiService) {
        this.aiService = aiService;
    }
    
    public KnowledgeGraphConfig getKgConfig() {
        return kgConfig;
    }
    
    public void setKgConfig(KnowledgeGraphConfig kgConfig) {
        this.kgConfig = kgConfig;
    }
    
    public MCPProtocolConfig getMcpConfig() {
        return mcpConfig;
    }
    
    public void setMcpConfig(MCPProtocolConfig mcpConfig) {
        this.mcpConfig = mcpConfig;
    }
    
    public EntityConfig getEntityConfig() {
        return entityConfig;
    }
    
    public void setEntityConfig(EntityConfig entityConfig) {
        this.entityConfig = entityConfig;
    }
    
    public SecurityConfig getSecurityConfig() {
        return securityConfig;
    }
    
    public void setSecurityConfig(SecurityConfig securityConfig) {
        this.securityConfig = securityConfig;
    }
    
    public DatabaseConfig getDatabaseConfig() {
        return databaseConfig;
    }
    
    public void setDatabaseConfig(DatabaseConfig databaseConfig) {
        this.databaseConfig = databaseConfig;
    }
    
    public ImportExportConfig getImportExportConfig() {
        return importExportConfig;
    }
    
    public void setImportExportConfig(ImportExportConfig importExportConfig) {
        this.importExportConfig = importExportConfig;
    }
    
    // ========== 内部配置类 ==========
    
    /**
     * AI服务配置
     */
    public static class AIServiceConfig {
        private String endpoint = "http://localhost:8080/api/ai";
        private String apiKey = "";
        private int timeout = 30000;
        private int retryCount = 3;
        private boolean batchProcessingEnabled = true;
        
        public String getEndpoint() {
            return endpoint;
        }
        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
        public String getApiKey() {
            return apiKey;
        }
        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
        public int getTimeout() {
            return timeout;
        }
        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }
        public int getRetryCount() {
            return retryCount;
        }
        public void setRetryCount(int retryCount) {
            this.retryCount = retryCount;
        }
        public boolean isBatchProcessingEnabled() {
            return batchProcessingEnabled;
        }
        public void setBatchProcessingEnabled(boolean batchProcessingEnabled) {
            this.batchProcessingEnabled = batchProcessingEnabled;
        }
    }
    
    /**
     * 知识图谱配置
     */
    public static class KnowledgeGraphConfig {
        private String graphDBUrl = "http://localhost:7474";
        private String username = "neo4j";
        private String password = "neo4j";
        private boolean autoSyncEnabled = false;
        private int syncBatchSize = 100;
        
        public String getGraphDBUrl() {
            return graphDBUrl;
        }
        public void setGraphDBUrl(String graphDBUrl) {
            this.graphDBUrl = graphDBUrl;
        }
        public String getUsername() {
            return username;
        }
        public void setUsername(String username) {
            this.username = username;
        }
        public String getPassword() {
            return password;
        }
        public void setPassword(String password) {
            this.password = password;
        }
        public boolean isAutoSyncEnabled() {
            return autoSyncEnabled;
        }
        public void setAutoSyncEnabled(boolean autoSyncEnabled) {
            this.autoSyncEnabled = autoSyncEnabled;
        }
        public int getSyncBatchSize() {
            return syncBatchSize;
        }
        public void setSyncBatchSize(int syncBatchSize) {
            this.syncBatchSize = syncBatchSize;
        }
    }
    
    /**
     * MCP协议配置
     */
    public static class MCPProtocolConfig {
        private String dataIngestionEndpoint = "/api/mcp/ingest";
        private String metadataTaggingEndpoint = "/api/mcp/tagging";
        private String interoperabilityEndpoint = "/api/mcp/interop";
        private String authToken = "";
        private int maxPayloadSize = 1048576; // 1MB
        private boolean sslEnabled = true;
        
        public String getDataIngestionEndpoint() {
            return dataIngestionEndpoint;
        }
        public void setDataIngestionEndpoint(String dataIngestionEndpoint) {
            this.dataIngestionEndpoint = dataIngestionEndpoint;
        }
        public String getMetadataTaggingEndpoint() {
            return metadataTaggingEndpoint;
        }
        public void setMetadataTaggingEndpoint(String metadataTaggingEndpoint) {
            this.metadataTaggingEndpoint = metadataTaggingEndpoint;
        }
        public String getInteroperabilityEndpoint() {
            return interoperabilityEndpoint;
        }
        public void setInteroperabilityEndpoint(String interoperabilityEndpoint) {
            this.interoperabilityEndpoint = interoperabilityEndpoint;
        }
        public String getAuthToken() {
            return authToken;
        }
        public void setAuthToken(String authToken) {
            this.authToken = authToken;
        }
        public int getMaxPayloadSize() {
            return maxPayloadSize;
        }
        public void setMaxPayloadSize(int maxPayloadSize) {
            this.maxPayloadSize = maxPayloadSize;
        }
        public boolean isSslEnabled() {
            return sslEnabled;
        }
        public void setSslEnabled(boolean sslEnabled) {
            this.sslEnabled = sslEnabled;
        }
    }
    
    /**
     * 实体配置
     */
    public static class EntityConfig {
        private int maxFieldsPerEntity = 200;
        private int maxRelationshipsPerEntity = 50;
        private int maxBusinessRulesPerEntity = 100;
        private int maxOperationsPerEntity = 50;
        private int maxIndexesPerEntity = 20;
        private boolean entityLevelAuditingEnabled = true;
        
        public int getMaxFieldsPerEntity() {
            return maxFieldsPerEntity;
        }
        public void setMaxFieldsPerEntity(int maxFieldsPerEntity) {
            this.maxFieldsPerEntity = maxFieldsPerEntity;
        }
        public int getMaxRelationshipsPerEntity() {
            return maxRelationshipsPerEntity;
        }
        public void setMaxRelationshipsPerEntity(int maxRelationshipsPerEntity) {
            this.maxRelationshipsPerEntity = maxRelationshipsPerEntity;
        }
        public int getMaxBusinessRulesPerEntity() {
            return maxBusinessRulesPerEntity;
        }
        public void setMaxBusinessRulesPerEntity(int maxBusinessRulesPerEntity) {
            this.maxBusinessRulesPerEntity = maxBusinessRulesPerEntity;
        }
        public int getMaxOperationsPerEntity() {
            return maxOperationsPerEntity;
        }
        public void setMaxOperationsPerEntity(int maxOperationsPerEntity) {
            this.maxOperationsPerEntity = maxOperationsPerEntity;
        }
        public int getMaxIndexesPerEntity() {
            return maxIndexesPerEntity;
        }
        public void setMaxIndexesPerEntity(int maxIndexesPerEntity) {
            this.maxIndexesPerEntity = maxIndexesPerEntity;
        }
        public boolean isEntityLevelAuditingEnabled() {
            return entityLevelAuditingEnabled;
        }
        public void setEntityLevelAuditingEnabled(boolean entityLevelAuditingEnabled) {
            this.entityLevelAuditingEnabled = entityLevelAuditingEnabled;
        }
    }
    
    /**
     * 安全配置
     */
    public static class SecurityConfig {
        private boolean fieldLevelEncryptionEnabled = true;
        private String encryptionAlgorithm = "AES/GCM/NoPadding";
        private boolean permissionCheckEnabled = true;
        private boolean auditLoggingEnabled = true;
        private boolean sensitiveFieldMaskingEnabled = true;
        
        public boolean isFieldLevelEncryptionEnabled() {
            return fieldLevelEncryptionEnabled;
        }
        public void setFieldLevelEncryptionEnabled(boolean fieldLevelEncryptionEnabled) {
            this.fieldLevelEncryptionEnabled = fieldLevelEncryptionEnabled;
        }
        public String getEncryptionAlgorithm() {
            return encryptionAlgorithm;
        }
        public void setEncryptionAlgorithm(String encryptionAlgorithm) {
            this.encryptionAlgorithm = encryptionAlgorithm;
        }
        public boolean isPermissionCheckEnabled() {
            return permissionCheckEnabled;
        }
        public void setPermissionCheckEnabled(boolean permissionCheckEnabled) {
            this.permissionCheckEnabled = permissionCheckEnabled;
        }
        public boolean isAuditLoggingEnabled() {
            return auditLoggingEnabled;
        }
        public void setAuditLoggingEnabled(boolean auditLoggingEnabled) {
            this.auditLoggingEnabled = auditLoggingEnabled;
        }
        public boolean isSensitiveFieldMaskingEnabled() {
            return sensitiveFieldMaskingEnabled;
        }
        public void setSensitiveFieldMaskingEnabled(boolean sensitiveFieldMaskingEnabled) {
            this.sensitiveFieldMaskingEnabled = sensitiveFieldMaskingEnabled;
        }
    }
    
    /**
     * 数据库配置
     */
    public static class DatabaseConfig {
        private boolean autoDdlEnabled = false;
        private boolean batchInsertEnabled = true;
        private int batchSize = 100;
        private boolean optimisticLockingEnabled = true;
        private boolean cascadeDeleteEnabled = false;
        
        public boolean isAutoDdlEnabled() {
            return autoDdlEnabled;
        }
        public void setAutoDdlEnabled(boolean autoDdlEnabled) {
            this.autoDdlEnabled = autoDdlEnabled;
        }
        public boolean isBatchInsertEnabled() {
            return batchInsertEnabled;
        }
        public void setBatchInsertEnabled(boolean batchInsertEnabled) {
            this.batchInsertEnabled = batchInsertEnabled;
        }
        public int getBatchSize() {
            return batchSize;
        }
        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
        public boolean isOptimisticLockingEnabled() {
            return optimisticLockingEnabled;
        }
        public void setOptimisticLockingEnabled(boolean optimisticLockingEnabled) {
            this.optimisticLockingEnabled = optimisticLockingEnabled;
        }
        public boolean isCascadeDeleteEnabled() {
            return cascadeDeleteEnabled;
        }
        public void setCascadeDeleteEnabled(boolean cascadeDeleteEnabled) {
            this.cascadeDeleteEnabled = cascadeDeleteEnabled;
        }
    }
    
    /**
     * 导入导出配置
     */
    public static class ImportExportConfig {
        private String defaultExportFormat = "JSON";
        private boolean importValidationStrictMode = true;
        private int maxImportFileSize = 10485760; // 10MB
        private boolean incrementalImportEnabled = true;
        private boolean importBackupEnabled = true;
        
        public String getDefaultExportFormat() {
            return defaultExportFormat;
        }
        public void setDefaultExportFormat(String defaultExportFormat) {
            this.defaultExportFormat = defaultExportFormat;
        }
        public boolean isImportValidationStrictMode() {
            return importValidationStrictMode;
        }
        public void setImportValidationStrictMode(boolean importValidationStrictMode) {
            this.importValidationStrictMode = importValidationStrictMode;
        }
        public int getMaxImportFileSize() {
            return maxImportFileSize;
        }
        public void setMaxImportFileSize(int maxImportFileSize) {
            this.maxImportFileSize = maxImportFileSize;
        }
        public boolean isIncrementalImportEnabled() {
            return incrementalImportEnabled;
        }
        public void setIncrementalImportEnabled(boolean incrementalImportEnabled) {
            this.incrementalImportEnabled = incrementalImportEnabled;
        }
        public boolean isImportBackupEnabled() {
            return importBackupEnabled;
        }
        public void setImportBackupEnabled(boolean importBackupEnabled) {
            this.importBackupEnabled = importBackupEnabled;
        }
    }
}