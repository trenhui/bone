# Studio Generator 详细设计方案

## 1. 模块概述

Studio Generator 是 BONE 平台的代码生成模块，提供基于元数据和数据库表结构的代码自动生成功能，帮助开发者快速生成符合 BONE 平台规范的代码，提高开发效率。

**核心目标**：
- 支持从数据库表结构自动生成代码
- 提供多种代码模板，满足不同业务场景需求
- 支持自定义模板和代码生成规则
- 提供代码预览和验证功能
- 集成到 BONE 平台的整体工作流中

## 2. 功能设计

### 2.1 数据源管理

**功能描述**：管理代码生成所需的数据库连接配置。

**详细功能**：
- **数据源配置**：支持配置多种数据库类型（MySQL、PostgreSQL、Oracle、SQL Server）的连接信息
- **测试连接**：验证数据源连接是否有效
- **数据源列表**：展示已配置的数据源
- **数据源编辑**：修改现有数据源配置
- **数据源删除**：删除不需要的数据源

**用户故事**：
> 作为开发者，我希望能够配置和管理数据库连接，以便系统能够读取表结构并生成代码。
> 作为团队负责人，我希望能够查看所有团队成员配置的数据源，以便统一管理和维护。
> 作为安全管理员，我希望数据源密码能够加密存储，以确保数据库连接信息的安全性。

### 2.2 表结构管理

**功能描述**：管理数据库表结构，用于代码生成。

**详细功能**：
- **表结构同步**：从数据源同步表结构信息
- **表结构预览**：查看表的字段、类型、约束等信息
- **表选择**：选择需要生成代码的表
- **批量操作**：支持批量选择和生成代码

**用户故事**：
> 作为开发者，我希望能够从数据库同步表结构，并选择需要生成代码的表，以便系统能够根据表结构生成相应的代码。
> 作为数据库管理员，我希望能够查看表的详细结构，包括字段类型、约束等信息，以便确保生成的代码符合数据库设计规范。
> 作为项目负责人，我希望能够批量选择多个表生成代码，以提高开发效率。

### 2.3 代码模板管理

**功能描述**：管理代码生成使用的模板。

**详细功能**：
- **模板列表**：展示可用的代码模板
- **模板预览**：查看模板的内容和结构
- **模板选择**：选择适合的代码模板
- **模板配置**：配置模板的生成参数

**用户故事**：
> 作为开发者，我希望能够选择适合的代码模板，以便生成符合项目规范的代码。
> 作为架构师，我希望能够预览模板内容，以便确保生成的代码符合架构设计规范。
> 作为团队负责人，我希望能够配置模板参数，以便生成的代码符合团队的编码规范。

### 2.4 代码生成

**功能描述**：根据表结构和模板生成代码。

**详细功能**：
- **生成配置**：配置生成代码的包路径、模块名称等参数
- **代码预览**：在生成前预览代码内容
- **代码生成**：生成代码文件
- **代码下载**：将生成的代码打包下载
- **生成历史**：记录代码生成的历史记录

**用户故事**：
> 作为开发者，我希望能够根据表结构和模板生成代码，并下载生成的代码文件，以便快速开始开发工作。
> 作为代码审查员，我希望能够在生成前预览代码内容，以便确保生成的代码质量。
> 作为项目管理者，我希望能够查看代码生成的历史记录，以便跟踪项目的开发进度。

## 3. 技术实现

### 3.1 前端技术

| 技术 | 版本 | 用途 |
|------|------|------|
| React | 18+ | 前端框架 |
| TypeScript | 5.2+ | 类型系统 |
| Vite | 5.0+ | 构建工具 |
| Ant Design | 5.12+ | UI组件库 |
| Redux Toolkit | 2.0+ | 状态管理 |
| React Router | 6.20+ | 路由管理 |
| Axios | 1.6+ | HTTP客户端 |

### 3.2 后端技术

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17+ | 核心开发语言 |
| Spring Boot | 3.2+ | 后端框架 |
| Spring Security | 6.2+ | 安全框架 |
| JPA | 3.1+ | ORM框架 |
| MyBatis | 3.5+ | ORM框架 |
| Freemarker | 2.3+ | 模板引擎 |
| JavaPoet | 1.13+ | 代码生成库 |
| bone-core | 1.0+ | 核心框架 |
| bone-metadata-sdk | 1.0+ | 元数据SDK |
| bone-extension-sdk | 1.0+ | 扩展SDK |

### 3.3 关键实现

#### 3.3.1 双模式 UseCase 策略

**Mode A（简单模式）**：适用于简单 CRUD 和边缘功能，UseCase 直写，逻辑内联。

**Mode B（企业模式）**：适用于核心能力、复用需求、AI 编排，UseCase 编排 + Handler 原子化。

**模式选择规则**：
- 需要暴露给 AI 的能力 → Mode B（使用 @Capability 注解）
- 涉及外部系统交互 → Mode B（使用 Gateway）
- 复杂业务逻辑 → Mode B（使用多个 Handler 编排）
- 简单 CRUD 和查询 → Mode A（直接实现）

#### 3.3.2 AI 可见性规范

**@Capability 注解**：用于标记需要暴露给 AI 的能力，包含完整元数据。

```java
@Capability(
    name = "GenerateCode",
    description = "根据表结构和模板生成代码",
    inputSchema = """
        {
            "type": "object",
            "properties": {
                "projectName": {"type": "string", "description": "项目名称"},
                "basePackage": {"type": "string", "description": "基础包路径"},
                "moduleName": {"type": "string", "description": "模块名称"},
                "tableNames": {"type": "array", "items": {"type": "string"}, "description": "表名列表"}
            },
            "required": ["projectName", "basePackage", "moduleName", "tableNames"]
        }
        """,
    outputSchema = """
        {
            "type": "object",
            "properties": {
                "success": {"type": "boolean", "description": "是否成功"},
                "message": {"type": "string", "description": "消息"},
                "generatedFiles": {"type": "array", "items": {"type": "object"}, "description": "生成的文件"},
                "zipFile": {"type": "string", "format": "binary", "description": "压缩文件"}
            }
        }
        """,
    idempotent = false,
    cost = 5,
    retryable = true,
    timeout = 60
)
@Component
@RequiredArgsConstructor
public class GenerateCodeHandler {
    private final CodeGeneratorService codeGeneratorService;
    
    public CodeGenerationResponse handle(GenerateCodeCommand command) {
        return codeGeneratorService.generateCode(command);
    }
}
```

**Handler Registry**：自动扫描和注册带有 @Capability 注解的处理器，提供能力元数据管理。

```java
@Component
public class HandlerRegistry {
    
    private final Map<String, CapabilityRegistration> capabilityIndex = new ConcurrentHashMap<>();
    private final ApplicationContext applicationContext;
    
    public HandlerRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    @EventListener(ContextRefreshedEvent.class)
    public void init() {
        Map<String, Object> handlers = applicationContext.getBeansWithAnnotation(Capability.class);
        handlers.forEach((beanName, handler) -> {
            Capability annotation = handler.getClass().getAnnotation(Capability.class);
            if (annotation != null) {
                CapabilityRegistration registration = CapabilityRegistration.builder()
                    .name(annotation.name())
                    .description(annotation.description())
                    .inputSchema(annotation.inputSchema())
                    .outputSchema(annotation.outputSchema())
                    .idempotent(annotation.idempotent())
                    .cost(annotation.cost())
                    .retryable(annotation.retryable())
                    .timeout(annotation.timeout())
                    .handlerInstance(handler)
                    .build();
                capabilityIndex.put(annotation.name(), registration);
            }
        });
    }
    
    public CapabilityRegistration getCapability(String name) {
        CapabilityRegistration registration = capabilityIndex.get(name);
        if (registration == null) {
            throw new CapabilityNotFoundException("Capability not found: " + name);
        }
        return registration;
    }
    
    public List<CapabilityRegistration> getAllCapabilities() {
        return new ArrayList<>(capabilityIndex.values());
    }
}
```

#### 3.3.3 数据源管理

```java
@Service
public class CodeGeneratorServiceImpl implements CodeGeneratorService {
    
    private static final Logger logger = LoggerFactory.getLogger(CodeGeneratorServiceImpl.class);
    
    @Override
    public boolean testConnection(DataSourceConfig config) {
        logger.info("测试数据源连接: type={}, host={}, port={}, database={}, username={}", 
                config.getType(), config.getHost(), config.getPort(), config.getDatabase(), config.getUsername());
        
        Connection connection = null;
        try {
            // 构建数据库连接URL
            String url = String.format("jdbc:%s://%s:%s/%s?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                    config.getType(), config.getHost(), config.getPort(), config.getDatabase());
            
            // 加载驱动并建立连接
            Class.forName(getDriverClassName(config.getType()));
            connection = DriverManager.getConnection(url, config.getUsername(), config.getPassword());
            
            logger.info("数据源连接测试成功");
            return true;
        } catch (Exception e) {
            logger.error("数据源连接测试失败: {}", e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    logger.error("关闭数据库连接失败: {}", e.getMessage());
                }
            }
        }
    }
    
    private String getDriverClassName(String databaseType) {
        switch (databaseType) {
            case "mysql":
                return "com.mysql.cj.jdbc.Driver";
            case "postgresql":
                return "org.postgresql.Driver";
            case "oracle":
                return "oracle.jdbc.OracleDriver";
            case "sqlserver":
                return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            default:
                return "com.mysql.cj.jdbc.Driver";
        }
    }
    
    // 其他方法...
}
```

#### 3.3.4 表结构管理

```java
@Override
public List<DatabaseTable> loadTables(DataSourceConfig config) {
    logger.info("开始从数据库加载表结构: type={}, host={}, port={}, database={}, username={}", 
            config.getType(), config.getHost(), config.getPort(), config.getDatabase(), config.getUsername());
    
    List<DatabaseTable> tables = new ArrayList<>();
    
    // 实际连接数据库并读取表结构
    Connection connection = null;
    try {
        // 构建数据库连接URL
        String url = String.format("jdbc:%s://%s:%s/%s?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true",
                config.getType(), config.getHost(), config.getPort(), config.getDatabase());
        
        // 加载驱动并建立连接
        Class.forName(getDriverClassName(config.getType()));
        connection = DriverManager.getConnection(url, config.getUsername(), config.getPassword());
        
        // 获取数据库元数据
        DatabaseMetaData metaData = connection.getMetaData();
        
        // 读取所有表
        ResultSet tablesResultSet = metaData.getTables(
                config.getDatabase(), // catalog
                null, // schemaPattern
                "%", // tableNamePattern
                new String[] {"TABLE"} // types
        );
        
        int tableId = 1;
        while (tablesResultSet.next()) {
            String tableName = tablesResultSet.getString("TABLE_NAME");
            String tableComment = tablesResultSet.getString("REMARKS");
            
            if (tableComment == null) {
                tableComment = "";
            }
            
            DatabaseTable table = DatabaseTable.builder()
                    .id(String.valueOf(tableId++))
                    .tableName(tableName)
                    .tableComment(tableComment)
                    .columns(loadTableColumns(config, tableName))
                    .build();
            
            tables.add(table);
        }
        
        logger.info("成功加载 {} 个表结构", tables.size());
        
    } catch (Exception e) {
        logger.error("加载表结构失败: {}", e.getMessage());
        // 如果连接失败，返回模拟数据作为 fallback
        tables = getMockTables();
    } finally {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.error("关闭数据库连接失败: {}", e.getMessage());
            }
        }
    }
    
    return tables;
}
```

#### 3.3.5 代码生成

```java
@Override
public CodeGenerationResponse generateCode(CodeGenerationRequest request) {
    logger.info("开始生成代码: projectName={}, basePackage={}, moduleName={}, tables={}", 
            request.getProjectName(), request.getBasePackage(), request.getModuleName(), request.getTableNames());
    
    CodeGenerationResponse response = new CodeGenerationResponse();
    List<GeneratedFile> generatedFiles = new ArrayList<>();
    
    try {
        // 1. 加载表结构
        List<DatabaseTable> tables = new ArrayList<>();
        for (String tableName : request.getTableNames()) {
            // 这里应该从数据源加载表结构，这里简化处理
            DatabaseTable table = mockTable(tableName);
            tables.add(table);
        }
        
        // 2. 生成代码
        for (DatabaseTable table : tables) {
            // 生成实体类
            generatedFiles.add(generateEntity(table, request.getBasePackage(), request.getModuleName()));
            
            // 生成Repository接口
            generatedFiles.add(generateRepository(table, request.getBasePackage(), request.getModuleName()));
            
            // 生成Service接口和实现
            generatedFiles.add(generateServiceInterface(table, request.getBasePackage(), request.getModuleName()));
            generatedFiles.add(generateServiceImpl(table, request.getBasePackage(), request.getModuleName()));
            
            // 生成Controller
            generatedFiles.add(generateController(table, request.getBasePackage(), request.getModuleName()));
        }
        
        // 3. 打包生成的文件
        byte[] zipBytes = packageFiles(generatedFiles);
        
        response.setSuccess(true);
        response.setMessage("代码生成成功");
        response.setGeneratedFiles(generatedFiles);
        response.setZipFile(zipBytes);
        
        logger.info("代码生成成功，共生成 {} 个文件", generatedFiles.size());
        
    } catch (Exception e) {
        logger.error("代码生成失败: {}", e.getMessage());
        response.setSuccess(false);
        response.setMessage("代码生成失败: " + e.getMessage());
    }
    
    return response;
}
```

## 4. 数据模型

### 4.1 核心模型

| 表名 | 描述 | 关键字段 |
|------|------|----------|
| `gen_code_template` | 代码生成模板 | id, name, code, description, type, content, status, created_at, updated_at |
| `gen_data_source` | 数据源配置 | id, name, type, host, port, db_name, username, password_encrypted, is_enabled, created_at, updated_at |
| `gen_generation_history` | 代码生成历史 | id, project_name, base_package, module_name, data_source_id, table_names, status, created_at |
| `gen_type_mapping` | 数据类型映射 | id, source_db_type, jdbc_type, java_type, full_java_type, created_at, updated_at |
| `gen_table_metadata` | 表结构元数据 | id, data_source_id, original_table_name, custom_entity_name, table_comment, sync_status, created_at, updated_at |
| `gen_column_metadata` | 列元数据 | id, table_metadata_id, original_column_name, custom_field_name, jdbc_type, java_type, column_type, is_nullable, is_primary_key, created_at, updated_at |

### 4.2 建表SQL

```sql
-- 数据源配置表
CREATE TABLE `gen_data_source` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
  `name` VARCHAR(200) NOT NULL COMMENT '数据源名称',
  `type` VARCHAR(50) NOT NULL COMMENT '数据库类型',
  `host` VARCHAR(255) NOT NULL COMMENT '主机地址',
  `port` INT NOT NULL COMMENT '端口',
  `db_name` VARCHAR(100) NOT NULL COMMENT '数据库名',
  `username` VARCHAR(100) NOT NULL COMMENT '用户名',
  `password_encrypted` VARCHAR(255) NOT NULL COMMENT '加密密码',
  `params` JSON DEFAULT NULL COMMENT '连接参数',
  `is_enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `last_test_at` DATETIME(3) DEFAULT NULL COMMENT '最后测试时间',
  `last_test_result` VARCHAR(20) DEFAULT NULL COMMENT '最后测试结果',
  `last_test_message` TEXT DEFAULT NULL COMMENT '最后测试消息',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '修改人ID',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gen_ds_tenant_name` (`tenant_id`, `name`),
  KEY `idx_gen_ds_type` (`type`),
  KEY `idx_gen_ds_enabled` (`is_enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='数据源配置表';

-- 代码生成模板表
CREATE TABLE `gen_code_template` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
  `name` VARCHAR(200) NOT NULL COMMENT '模板名称',
  `code` VARCHAR(200) NOT NULL COMMENT '模板编码',
  `description` TEXT DEFAULT NULL COMMENT '模板描述',
  `type` VARCHAR(50) NOT NULL COMMENT '模板类型',
  `language` VARCHAR(20) NOT NULL DEFAULT 'java' COMMENT '语言',
  `engine` VARCHAR(20) NOT NULL DEFAULT 'Freemarker' COMMENT '模板引擎',
  `version` VARCHAR(50) NOT NULL COMMENT '版本',
  `content` MEDIUMTEXT NOT NULL COMMENT '模板内容',
  `sample_output` MEDIUMTEXT DEFAULT NULL COMMENT '示例输出',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态',
  `published_at` DATETIME(3) DEFAULT NULL COMMENT '发布时间',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '修改人ID',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除',
  `version_lock` INT NOT NULL DEFAULT 0 COMMENT '版本锁',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gen_ct_tenant_code_version` (`tenant_id`, `code`, `version`),
  KEY `idx_gen_ct_type` (`type`),
  KEY `idx_gen_ct_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='代码生成模板表';

-- 代码生成历史表
CREATE TABLE `gen_generation_history` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键',
  `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
  `project_name` VARCHAR(200) NOT NULL COMMENT '项目名称',
  `base_package` VARCHAR(200) NOT NULL COMMENT '基础包路径',
  `module_name` VARCHAR(200) NOT NULL COMMENT '模块名称',
  `data_source_id` BIGINT DEFAULT NULL COMMENT '数据源ID',
  `table_names` JSON NOT NULL COMMENT '生成的表名列表',
  `template_ids` JSON DEFAULT NULL COMMENT '模板ID列表',
  `gen_config` JSON DEFAULT NULL COMMENT '生成配置',
  `generated_files` JSON DEFAULT NULL COMMENT '生成的文件',
  `zip_url` VARCHAR(500) DEFAULT NULL COMMENT '压缩包URL',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态',
  `error_message` TEXT DEFAULT NULL COMMENT '错误信息',
  `started_at` DATETIME(3) NOT NULL COMMENT '开始时间',
  `completed_at` DATETIME(3) DEFAULT NULL COMMENT '完成时间',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_gen_gh_tenant_project` (`tenant_id`, `project_name`),
  KEY `idx_gen_gh_status` (`status`),
  KEY `idx_gen_gh_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='代码生成历史表';

-- 数据类型映射表
CREATE TABLE `gen_type_mapping` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
  `source_db_type` VARCHAR(50) NOT NULL COMMENT '源数据库类型',
  `jdbc_type` VARCHAR(50) NOT NULL COMMENT 'JDBC类型',
  `java_type` VARCHAR(50) NOT NULL COMMENT 'Java类型',
  `full_java_type` VARCHAR(255) NOT NULL COMMENT '完整Java类型',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '修改人ID',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gen_tm_tenant_type_jdbc` (`tenant_id`, `source_db_type`, `jdbc_type`),
  KEY `idx_gen_tm_source_db` (`source_db_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='数据类型映射表';

-- 表结构元数据表
CREATE TABLE `gen_table_metadata` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键',
  `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
  `data_source_id` BIGINT NOT NULL COMMENT '数据源ID',
  `table_schema` VARCHAR(64) DEFAULT NULL COMMENT '表 schema',
  `original_table_name` VARCHAR(200) NOT NULL COMMENT '原始表名',
  `custom_entity_name` VARCHAR(200) DEFAULT NULL COMMENT '自定义实体名',
  `module_name` VARCHAR(200) DEFAULT NULL COMMENT '模块名称',
  `table_comment` VARCHAR(500) DEFAULT NULL COMMENT '表注释',
  `sync_status` TINYINT NOT NULL DEFAULT 0 COMMENT '同步状态',
  `last_sync_at` DATETIME(3) DEFAULT NULL COMMENT '最后同步时间',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '修改人ID',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gen_tmd_tenant_ds_table` (`tenant_id`, `data_source_id`, `original_table_name`),
  KEY `idx_gen_tmd_data_source` (`data_source_id`),
  KEY `idx_gen_tmd_sync_status` (`sync_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='表结构元数据表';

-- 列元数据表
CREATE TABLE `gen_column_metadata` (
  `id` BIGINT NOT NULL COMMENT 'Snowflake 主键',
  `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
  `table_metadata_id` BIGINT NOT NULL COMMENT '表元数据ID',
  `original_column_name` VARCHAR(100) NOT NULL COMMENT '原始列名',
  `custom_field_name` VARCHAR(100) DEFAULT NULL COMMENT '自定义字段名',
  `jdbc_type` VARCHAR(50) NOT NULL COMMENT 'JDBC类型',
  `java_type` VARCHAR(50) NOT NULL COMMENT 'Java类型',
  `column_type` VARCHAR(100) NOT NULL COMMENT '列类型',
  `column_length` INT DEFAULT NULL COMMENT '列长度',
  `precision` INT DEFAULT NULL COMMENT '精度',
  `scale` INT DEFAULT NULL COMMENT '小数位',
  `is_nullable` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可空',
  `is_primary_key` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否主键',
  `is_autoincrement` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否自增',
  `default_value` VARCHAR(255) DEFAULT NULL COMMENT '默认值',
  `column_comment` VARCHAR(500) DEFAULT NULL COMMENT '列注释',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人ID',
  `updated_by` BIGINT DEFAULT NULL COMMENT '修改人ID',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除',
  `version` INT NOT NULL DEFAULT 0 COMMENT '版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_gen_cmd_table_column` (`table_metadata_id`, `original_column_name`),
  KEY `idx_gen_cmd_table` (`table_metadata_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='列元数据表';
```

### 4.2 领域模型

**CodeTemplate.java**（聚合根）
- 代码模板领域模型
- 包含名称、描述、内容、类型等属性
- 继承 `bone-core` 的 `AggregateRoot`

**DataSource.java**（聚合根）
- 数据源领域模型
- 包含名称、类型、主机、端口、数据库、用户名、密码等属性
- 继承 `bone-core` 的 `AggregateRoot`

**DatabaseTable.java**（值对象）
- 数据库表领域模型
- 包含表名、表注释、列信息等属性

**TableColumn.java**（值对象）
- 表列领域模型
- 包含列名、数据类型、列注释、是否主键、是否可空、长度等属性

**CodeGenerationRequest.java**（值对象）
- 代码生成请求领域模型
- 包含项目名称、基础包路径、模块名称、表名列表等属性

**CodeGenerationResponse.java**（值对象）
- 代码生成响应领域模型
- 包含成功状态、消息、生成的文件列表、压缩文件等属性

## 5. API 设计

### 5.1 数据源 API

| API路径 | 方法 | 功能 | 权限 |
|---------|------|------|------|
| `/api/v1/generator/data-sources` | GET | 获取数据源列表 | 已认证 |
| `/api/v1/generator/data-sources` | POST | 创建数据源 | 已认证 |
| `/api/v1/generator/data-sources/{id}` | GET | 获取数据源详情 | 已认证 |
| `/api/v1/generator/data-sources/{id}` | PUT | 更新数据源 | 已认证 |
| `/api/v1/generator/data-sources/{id}` | DELETE | 删除数据源 | 已认证 |
| `/api/v1/generator/test-connection` | POST | 测试数据源连接 | 已认证 |

### 5.2 表结构 API

| API路径 | 方法 | 功能 | 权限 |
|---------|------|------|------|
| `/api/v1/generator/load-tables` | POST | 加载表结构 | 已认证 |
| `/api/v1/generator/load-columns` | POST | 加载表列结构 | 已认证 |

### 5.3 模板 API

| API路径 | 方法 | 功能 | 权限 |
|---------|------|------|------|
| `/api/v1/generator/templates` | GET | 获取模板列表 | 已认证 |
| `/api/v1/generator/templates/{id}` | GET | 获取模板详情 | 已认证 |
| `/api/v1/generator/templates/preview` | POST | 预览模板 | 已认证 |
| `/api/v1/generator/templates/validate` | POST | 验证模板 | 已认证 |

### 5.4 代码生成 API

| API路径 | 方法 | 功能 | 权限 |
|---------|------|------|------|
| `/api/v1/generator/generate` | POST | 生成代码 | 已认证 |
| `/api/v1/generator/history` | GET | 获取生成历史 | 已认证 |
| `/api/v1/generator/history/{id}` | GET | 获取生成历史详情 | 已认证 |

### 5.5 能力管理 API

| API路径 | 方法 | 功能 | 权限 |
|---------|------|------|------|
| `/api/v1/generator/capabilities` | GET | 获取所有能力 | 已认证 |
| `/api/v1/generator/capabilities/{name}` | GET | 获取指定能力 | 已认证 |

## 6. 部署与集成

### 6.1 部署方式

- **容器化部署**：使用 Docker 容器部署，通过 Kubernetes 进行编排
- **资源需求**：
  - CPU：1 核
  - 内存：1GB
  - 存储：10GB

### 6.2 集成点

- **与元数据服务集成**：获取元数据信息
- **与认证系统集成**：使用 IAM 服务进行权限控制
- **与配置系统集成**：通过 Nacos 管理配置

### 6.3 配置项

| 配置项 | 类型 | 默认值 | 描述 |
|--------|------|--------|------|
| `generator.template.dir` | String | `classpath:/templates` | 模板目录 |
| `generator.output.dir` | String | `./generated` | 输出目录 |
| `generator.default.package` | String | `com.bone` | 默认包路径 |
| `generator.timeout` | Integer | 30 | 生成超时时间（秒） |

## 7. 性能与安全

### 7.1 性能优化

- **缓存策略**：缓存数据库表结构信息，减少数据库查询
- **异步处理**：代码生成采用异步方式，避免阻塞主线程
- **批量操作**：支持批量表结构加载和代码生成
- **资源限制**：限制单个代码生成任务的资源使用

### 7.2 安全措施

- **权限控制**：基于 RBAC 的细粒度权限控制
- **密码加密**：数据源密码加密存储
- **输入验证**：对用户输入进行验证，防止注入攻击
- **审计日志**：记录所有代码生成操作，支持审计追踪
- **XSS 防护**：对生成的代码进行过滤和转义
- **CSRF 防护**：使用 CSRF token 保护 API

## 8. 测试计划

### 8.1 单元测试

- **测试范围**：核心服务和工具类
- **测试工具**：JUnit 5 + Mockito
- **覆盖率目标**：≥80%

### 8.2 集成测试

- **测试范围**：API 接口和服务间集成
- **测试工具**：Spring Boot Test
- **覆盖率目标**：≥60%

### 8.3 端到端测试

- **测试范围**：完整用户流程
- **测试工具**：Selenium + TestNG
- **测试场景**：
  - 数据源配置和测试
  - 表结构同步和选择
  - 代码模板选择和配置
  - 代码生成和下载

## 9. 监控与告警

### 9.1 监控指标

- **代码生成指标**：生成次数、成功率、平均生成时间
- **数据源指标**：连接测试次数、成功率
- **系统指标**：CPU 使用率、内存使用率、磁盘使用率

### 9.2 告警机制

- **告警规则**：基于 Prometheus 告警规则
- **告警级别**：严重、警告、信息
- **告警渠道**：邮件、短信、企业微信
- **告警场景**：
  - 代码生成失败
  - 数据源连接失败
  - 系统资源使用率过高

## 10. 微服务与Java工程结构

### 10.1 微服务设计

**微服务名称**：studio-generator

**服务职责**：
- 代码生成功能
- 数据源管理
- 表结构管理
- 模板管理
- 代码生成历史管理

**API前缀**：`/api/v1/generator`

**数据库**：`generator_db`

**端口**：8085

### 10.2 Java工程结构

采用 Bone-Blueprint v4.0 DDD 工程规范，遵循六边形架构 + CQRS + Bone Metadata SDK 原生集成：

```
bone-engine/studio-generator/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── bone/
│   │   │           └── studio/
│   │   │               └── generator/
│   │   │                   ├── StudioGeneratorApplication.java  # 应用入口
│   │   │                   │
│   │   │                   ├── adapter/               # 入站适配器层
│   │   │                   │   └── web/
│   │   │                   │       ├── controller/    # 控制器
│   │   │                   │       │   ├── DataSourceController.java
│   │   │                   │       │   ├── CodeGeneratorController.java
│   │   │                   │       │   ├── TemplateController.java
│   │   │                   │       │   └── HistoryController.java
│   │   │                   │       ├── dto/          # DTO
│   │   │                   │       │   ├── req/       # 请求DTO
│   │   │                   │       │   │   ├── CreateDataSourceReq.java
│   │   │                   │       │   │   ├── UpdateDataSourceReq.java
│   │   │                   │       │   │   ├── TestConnectionReq.java
│   │   │                   │       │   │   ├── LoadTablesReq.java
│   │   │                   │       │   │   ├── GenerateCodeReq.java
│   │   │                   │       │   │   └── PreviewTemplateReq.java
│   │   │                   │       │   └── resp/      # 响应DTO
│   │   │                   │       │       ├── DataSourceListResp.java
│   │   │                   │       │       ├── TableListResp.java
│   │   │                   │       │       ├── CodeGenerationResp.java
│   │   │                   │       │       └── TemplateListResp.java
│   │   │                   │       └── converter/    # 转换器
│   │   │                   │           ├── DataSourceWebConverter.java
│   │   │                   │           ├── CodeGeneratorWebConverter.java
│   │   │                   │           └── TemplateWebConverter.java
│   │   │                   │
│   │   │                   ├── application/            # 应用层
│   │   │                   │   ├── command/           # 命令
│   │   │                   │   │   ├── cmd/            # 命令对象
│   │   │                   │   │   │   ├── CreateDataSourceCmd.java
│   │   │                   │   │   │   ├── UpdateDataSourceCmd.java
│   │   │                   │   │   │   ├── DeleteDataSourceCmd.java
│   │   │                   │   │   │   └── GenerateCodeCmd.java
│   │   │                   │   │   └── handler/        # 命令处理器
│   │   │                   │   │       ├── CreateDataSourceHandler.java
│   │   │                   │   │       ├── UpdateDataSourceHandler.java
│   │   │                   │   │       ├── DeleteDataSourceHandler.java
│   │   │                   │   │       └── GenerateCodeHandler.java
│   │   │                   │   ├── query/             # 查询
│   │   │                   │   │   ├── qry/            # 查询对象
│   │   │                   │   │   │   ├── DataSourcePageQry.java
│   │   │                   │   │   │   ├── DataSourceByIdQry.java
│   │   │                   │   │   │   ├── TableListQry.java
│   │   │                   │   │   │   ├── TemplateListQry.java
│   │   │                   │   │   │   └── HistoryPageQry.java
│   │   │                   │   │   ├── handler/        # 查询处理器
│   │   │                   │   │   │   ├── DataSourcePageQueryHandler.java
│   │   │                   │   │   │   ├── DataSourceDetailQueryHandler.java
│   │   │                   │   │   │   ├── TableListQueryHandler.java
│   │   │                   │   │   │   ├── TemplateListQueryHandler.java
│   │   │                   │   │   │   └── HistoryPageQueryHandler.java
│   │   │                   │   │   └── dto/            # 查询结果DTO
│   │   │                   │   │       ├── DataSourceDTO.java
│   │   │                   │   │       ├── TableDTO.java
│   │   │                   │   │       ├── TemplateDTO.java
│   │   │                   │   │       └── HistoryDTO.java
│   │   │                   │   └── usecase/           # 用例
│   │   │                   │       ├── simple/         # Mode A 简单模式
│   │   │                   │       │   ├── GetDataSourceListUseCase.java
│   │   │                   │       │   ├── GetDataSourceDetailUseCase.java
│   │   │                   │       │   └── GetTemplateListUseCase.java
│   │   │                   │       ├── standard/       # Mode B 企业模式
│   │   │                   │       │   ├── CreateDataSourceUseCase.java
│   │   │                   │       │   ├── UpdateDataSourceUseCase.java
│   │   │                   │       │   ├── DeleteDataSourceUseCase.java
│   │   │                   │       │   └── GenerateCodeUseCase.java
│   │   │                   │       └── UseCaseExecutor.java
│   │   │                   │
│   │   │                   ├── domain/                # 领域层（零依赖）
│   │   │                   │   ├── model/             # 领域模型
│   │   │                   │   │   ├── code/          # 代码生成领域
│   │   │                   │   │   │   ├── CodeTemplate.java           # 聚合根
│   │   │                   │   │   │   ├── CodeGenerationRequest.java   # 值对象
│   │   │                   │   │   │   ├── CodeGenerationResponse.java  # 值对象
│   │   │                   │   │   │   ├── GeneratedFile.java          # 值对象
│   │   │                   │   │   │   └── event/                   # 领域事件
│   │   │                   │   │   │       └── CodeGeneratedEvent.java
│   │   │                   │   │   ├── data/          # 数据源领域
│   │   │                   │   │   │   ├── DataSource.java            # 聚合根
│   │   │                   │   │   │   ├── DataSourceConfig.java       # 值对象
│   │   │                   │   │   │   ├── DatabaseTable.java          # 值对象
│   │   │                   │   │   │   ├── TableColumn.java            # 值对象
│   │   │                   │   │   │   └── event/                   # 领域事件
│   │   │                   │   │   │       └── DataSourceCreatedEvent.java
│   │   │                   │   │   └── history/       # 历史记录领域
│   │   │                   │   │       ├── CodeGenerationHistory.java  # 聚合根
│   │   │                   │   │       └── event/                   # 领域事件
│   │   │                   │   │           └── CodeGenerationCompletedEvent.java
│   │   │                   │   ├── repository/       # 仓储接口
│   │   │                   │   │   ├── CodeTemplateRepository.java
│   │   │                   │   │   ├── DataSourceRepository.java
│   │   │                   │   │   └── CodeGenerationHistoryRepository.java
│   │   │                   │   └── service/          # 领域服务
│   │   │                   │       ├── CodeGeneratorService.java
│   │   │                   │       ├── DataSourceService.java
│   │   │                   │       └── TemplateService.java
│   │   │                   │
│   │   │                   ├── infrastructure/       # 基础设施层
│   │   │                   │   ├── persistence/       # 持久化
│   │   │                   │   │   ├── CodeTemplateRepositoryImpl.java
│   │   │                   │   │   ├── DataSourceRepositoryImpl.java
│   │   │                   │   │   └── CodeGenerationHistoryRepositoryImpl.java
│   │   │                   │   ├── service/          # 服务实现
│   │   │                   │   │   ├── CodeGeneratorServiceImpl.java
│   │   │                   │   │   ├── DataSourceServiceImpl.java
│   │   │                   │   │   └── TemplateServiceImpl.java
│   │   │                   │   └── config/           # 配置
│   │   │                   │       ├── GeneratorConfiguration.java
│   │   │                   │       ├── SecurityConfig.java
│   │   │                   │       └── SwaggerConfig.java
│   │   │                   │
│   │   │                   └── common/               # 应用级通用组件
│   │   │                       ├── exception/        # 异常
│   │   │                       │   ├── BusinessException.java
│   │   │                       │   ├── NotFoundException.java
│   │   │                       │   └── SystemException.java
│   │   │                       ├── result/           # 响应
│   │   │                       │   ├── ApiResponse.java
│   │   │                       │   └── PageResult.java
│   │   │                       └── util/             # 工具
│   │   │                           ├── CodeGeneratorUtils.java
│   │   │                           └── DatabaseUtils.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       └── templates/         # 代码模板
│   │           ├── entity.ftl
│   │           ├── repository.ftl
│   │           ├── service.ftl
│   │           ├── serviceImpl.ftl
│   │           └── controller.ftl
│   └── test/            # 测试代码
│       └── java/
│           └── com/
│               └── bone/
│                   └── studio/
│                       └── generator/
│                           ├── adapter/
│                           ├── application/
│                           ├── domain/
│                           └── infrastructure/
├── pom.xml             # Maven配置
├── README.md           # 服务说明
└── Dockerfile          # Docker构建文件
```

### 10.3 核心类设计

#### 10.3.1 控制器类（Adapter层）

**DataSourceController.java**
- 管理数据源相关API
- 处理数据源的创建、查询、更新、删除操作
- 处理数据源连接测试
- 位于 `adapter/web/controller/` 目录
- 命名规范：`{Domain}Controller`
- 仅依赖 UseCase，不直接依赖 Handler

**CodeGeneratorController.java**
- 管理代码生成相关API
- 处理表结构加载、代码生成等操作
- 位于 `adapter/web/controller/` 目录
- 命名规范：`{Domain}Controller`
- 仅依赖 UseCase，不直接依赖 Handler

**TemplateController.java**
- 管理模板相关API
- 处理模板的查询、预览、验证等操作
- 位于 `adapter/web/controller/` 目录
- 命名规范：`{Domain}Controller`
- 仅依赖 UseCase，不直接依赖 Handler

**HistoryController.java**
- 管理代码生成历史相关API
- 处理生成历史的查询操作
- 位于 `adapter/web/controller/` 目录
- 命名规范：`{Domain}Controller`
- 仅依赖 UseCase，不直接依赖 Handler

**CapabilityController.java**
- 管理能力相关API
- 处理能力的查询操作
- 位于 `adapter/web/controller/` 目录
- 命名规范：`{Domain}Controller`
- 依赖 HandlerRegistry，用于AI能力发现

#### 10.3.2 命令和查询处理器（Application层）

**CreateDataSourceHandler.java**
- 处理创建数据源的命令
- 位于 `application/command/handler/` 目录
- 命名规范：`{Verb}{Domain}Handler`
- 必须使用 `@Transactional` 注解

**UpdateDataSourceHandler.java**
- 处理更新数据源的命令
- 位于 `application/command/handler/` 目录
- 命名规范：`{Verb}{Domain}Handler`
- 必须使用 `@Transactional` 注解

**DeleteDataSourceHandler.java**
- 处理删除数据源的命令
- 位于 `application/command/handler/` 目录
- 命名规范：`{Verb}{Domain}Handler`
- 必须使用 `@Transactional` 注解

**GenerateCodeHandler.java**
- 处理生成代码的命令
- 位于 `application/command/handler/` 目录
- 命名规范：`{Verb}{Domain}Handler`
- 必须使用 `@Transactional` 注解
- 使用 `@Capability` 注解标记为AI可见能力

**DataSourcePageQueryHandler.java**
- 处理数据源分页查询
- 位于 `application/query/handler/` 目录
- 命名规范：`{Domain}{Action}QueryHandler`
- 使用 `@Transactional(readOnly = true)` 注解

**TableListQueryHandler.java**
- 处理表结构列表查询
- 位于 `application/query/handler/` 目录
- 命名规范：`{Domain}{Action}QueryHandler`
- 使用 `@Transactional(readOnly = true)` 注解

#### 10.3.3 领域模型（Domain层）

**CodeTemplate.java**（聚合根）
- 代码模板领域模型
- 包含名称、描述、内容、类型等属性
- 继承 `bone-core` 的 `AggregateRoot`
- 仅使用 `@Getter` 和私有构造器
- 使用 `@Table` 注解显式绑定数据库表名
- 位于 `domain/model/code/` 目录
- 命名规范：`{Domain}`

**DataSource.java**（聚合根）
- 数据源领域模型
- 包含名称、类型、主机、端口、数据库、用户名、密码等属性
- 继承 `bone-core` 的 `AggregateRoot`
- 仅使用 `@Getter` 和私有构造器
- 使用 `@Table` 注解显式绑定数据库表名
- 位于 `domain/model/data/` 目录
- 命名规范：`{Domain}`

**DatabaseTable.java**（值对象）
- 数据库表领域模型
- 包含表名、表注释、列信息等属性
- 使用不可变记录类型
- 位于 `domain/model/data/` 目录
- 命名规范：`{BusinessConcept}`

**TableColumn.java**（值对象）
- 表列领域模型
- 包含列名、数据类型、列注释、是否主键、是否可空、长度等属性
- 使用不可变记录类型
- 位于 `domain/model/data/` 目录
- 命名规范：`{BusinessConcept}`

**CodeGenerationRequest.java**（值对象）
- 代码生成请求领域模型
- 包含项目名称、基础包路径、模块名称、表名列表等属性
- 使用不可变记录类型
- 位于 `domain/model/code/` 目录
- 命名规范：`{BusinessConcept}`

**CodeGenerationResponse.java**（值对象）
- 代码生成响应领域模型
- 包含成功状态、消息、生成的文件列表、压缩文件等属性
- 使用不可变记录类型
- 位于 `domain/model/code/` 目录
- 命名规范：`{BusinessConcept}`

**CodeGenerationHistory.java**（聚合根）
- 代码生成历史领域模型
- 包含项目名称、基础包路径、模块名称、表名列表、状态等属性
- 继承 `bone-core` 的 `AggregateRoot`
- 仅使用 `@Getter` 和私有构造器
- 使用 `@Table` 注解显式绑定数据库表名
- 位于 `domain/model/history/` 目录
- 命名规范：`{Domain}`

#### 10.3.4 领域事件（Domain层）

**CodeGeneratedEvent.java**
- 代码生成完成事件
- 实现 `bone-core` 的 `DomainEvent`
- 位于 `domain/model/code/event/` 目录
- 命名规范：`{Domain}{PastVerb}Event`

**DataSourceCreatedEvent.java**
- 数据源创建事件
- 实现 `bone-core` 的 `DomainEvent`
- 位于 `domain/model/data/event/` 目录
- 命名规范：`{Domain}{PastVerb}Event`

**CodeGenerationCompletedEvent.java**
- 代码生成历史创建事件
- 实现 `bone-core` 的 `DomainEvent`
- 位于 `domain/model/history/event/` 目录
- 命名规范：`{Domain}{PastVerb}Event`

#### 10.3.5 仓储接口（Domain层）

**CodeTemplateRepository.java**
- 代码模板仓储接口
- 继承 `bone-metadata-sdk` 的 `Repository`
- 空接口，不添加任何自定义方法
- 位于 `domain/repository/` 目录
- 命名规范：`{Aggregate}Repository`

**DataSourceRepository.java**
- 数据源仓储接口
- 继承 `bone-metadata-sdk` 的 `Repository`
- 空接口，不添加任何自定义方法
- 位于 `domain/repository/` 目录
- 命名规范：`{Aggregate}Repository`

**CodeGenerationHistoryRepository.java**
- 代码生成历史仓储接口
- 继承 `bone-metadata-sdk` 的 `Repository`
- 空接口，不添加任何自定义方法
- 位于 `domain/repository/` 目录
- 命名规范：`{Aggregate}Repository`

#### 10.3.6 服务接口和实现（Domain层和Infrastructure层）

**CodeGeneratorService.java**（接口）
- 代码生成服务接口
- 定义代码生成相关方法
- 位于 `domain/service/` 目录
- 命名规范：`{Domain}Service`

**CodeGeneratorServiceImpl.java**（实现）
- 代码生成服务实现
- 实现代码生成、表结构加载等功能
- 位于 `infrastructure/service/` 目录
- 命名规范：`{Domain}ServiceImpl`

**DataSourceService.java**（接口）
- 数据源服务接口
- 定义数据源管理相关方法
- 位于 `domain/service/` 目录
- 命名规范：`{Domain}Service`

**DataSourceServiceImpl.java**（实现）
- 数据源服务实现
- 实现数据源管理、连接测试等功能
- 位于 `infrastructure/service/` 目录
- 命名规范：`{Domain}ServiceImpl`

**TemplateService.java**（接口）
- 模板服务接口
- 定义模板管理相关方法
- 位于 `domain/service/` 目录
- 命名规范：`{Domain}Service`

**TemplateServiceImpl.java**（实现）
- 模板服务实现
- 实现模板管理、预览、验证等功能
- 位于 `infrastructure/service/` 目录
- 命名规范：`{Domain}ServiceImpl`

### 10.4 与其他微服务集成

**服务依赖**：
- **authz-service**：用于权限验证和用户认证
- **metadata-service**：获取元数据相关信息

**集成方式**：
- **Feign客户端**：通过 `infrastructure/client/` 目录下的客户端类调用其他微服务
- **消息队列**：通过领域事件机制接收其他服务的事件
- **共享缓存**：通过Redis共享跨服务数据
- **API网关**：通过API网关统一调用其他微服务

**集成流程**：
1. **权限验证**：调用authz-service进行用户认证和权限检查
2. **元数据获取**：调用metadata-service获取元数据相关信息
3. **数据同步**：通过消息队列接收其他服务的事件，更新本地缓存
4. **服务发现**：通过Nacos服务发现机制找到其他微服务

### 10.5 部署配置

**Dockerfile**：
```dockerfile
FROM openjdk:17-jdk-alpine
WORKDIR /app
COPY target/bone-studio-generator.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Kubernetes部署**：
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: studio-generator

spec:
  replicas: 2
  selector:
    matchLabels:
      app: studio-generator
  template:
    metadata:
      labels:
        app: studio-generator
    spec:
      containers:
      - name: studio-generator
        image: bone-studio-generator:latest
        ports:
        - containerPort: 8085
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: DB_HOST
          value: "mysql"
        - name: DB_PORT
          value: "3306"
        - name: NACOS_SERVER_ADDR
          value: "nacos:8848"
        - name: REDIS_HOST
          value: "redis"
        - name: REDIS_PORT
          value: "6379"
        resources:
          requests:
            cpu: "1000m"
            memory: "1Gi"
          limits:
            cpu: "2000m"
            memory: "2Gi"
```

**服务配置**：
```yaml
apiVersion: v1
kind: Service
metadata:
  name: studio-generator
spec:
  selector:
    app: studio-generator
  ports:
  - port: 8085
    targetPort: 8085
  type: ClusterIP
```

**Ingress配置**：
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: studio-generator-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
spec:
  rules:
  - host: bone.example.com
    http:
      paths:
      - path: /api/v1/generator
        pathType: Prefix
        backend:
          service:
            name: studio-generator
            port:
              number: 8085
```

## 11. UI 设计

### 11.1 设计原则

**核心设计理念**：
- **一致性**：遵循 BONE 平台统一的设计语言和交互模式
- **简洁性**：清晰、简洁的界面，减少视觉干扰，突出核心功能
- **可访问性**：符合 WCAG 2.1 AA 级标准，确保所有用户都能使用
- **响应式**：适配不同屏幕尺寸，提供一致的跨设备体验
- **性能优先**：优化加载速度和交互响应，提升用户体验
- **可扩展性**：支持主题定制和功能扩展，满足不同业务需求

**设计价值观**：
- **专业**：符合企业级应用的严谨性和可靠性，体现专业形象
- **现代**：采用现代前端设计趋势，保持界面的新鲜感
- **高效**：简化操作流程，提高用户工作效率
- **可靠**：提供清晰的视觉反馈和错误处理，增强用户信心
- **创新**：在保持一致性的基础上，探索创新的交互方式

### 11.2 布局系统

#### 11.2.1 整体布局

**布局结构**：
- **顶部状态栏**：高度 64px，包含系统标题、用户信息、通知中心等
- **左侧导航栏**：宽度 240px（展开）/ 80px（折叠），包含模块导航菜单
- **右侧内容区**：自适应宽度，包含页面内容
- **页脚**：高度 48px，包含版权信息、版本号等

**布局层次**：
- **Z-Index 管理**：
  - 基础层级：0
  - 导航栏：100
  - 状态栏：110
  - 弹窗：1050
  - 提示框：1070

**间距规范**：
- **内边距**：页面内容区域内边距 16px
- **组件间距**：组件之间间距 8px-16px
- **卡片间距**：卡片之间间距 16px
- **行高**：1.5-1.6

#### 11.2.2 组件布局

**卡片布局**：
- 卡片圆角：8px
- 卡片阴影：轻微阴影，hover 时阴影加深
- 卡片内边距：16px
- 卡片标题：16px，加粗

**表单布局**：
- 表单项间距：16px
- 标签宽度：120px（水平布局）
- 输入框高度：32px
- 按钮高度：32px

**表格布局**：
- 行高：48px
- 列间距：16px
- 表头背景：浅灰色
- 斑马线效果：交替行背景色

**弹窗布局**：
- 弹窗宽度：根据内容自适应，最大宽度 800px
- 弹窗内边距：24px
- 按钮区域：靠右对齐，按钮间距 8px

#### 11.2.3 响应式布局策略

**断点适配**：
- **xs** (< 640px)：
  - 导航栏：自动折叠为抽屉式
  - 内容区：单栏布局，所有元素垂直排列
  - 表单：垂直布局
  - 表格：自动适应宽度，必要时横向滚动

- **sm** (640px-767px)：
  - 导航栏：可折叠，默认折叠
  - 内容区：双栏布局开始
  - 表单：垂直布局
  - 表格：显示核心列

- **md** (768px-1023px)：
  - 导航栏：可折叠，默认展开
  - 内容区：双栏布局
  - 表单：水平布局
  - 表格：显示完整列

- **lg** (1024px-1279px)：
  - 导航栏：固定展开
  - 内容区：三栏布局
  - 表单：水平布局
  - 表格：显示完整列，支持列宽调整

- **xl** (1280px-1535px)：
  - 导航栏：固定展开
  - 内容区：三栏布局，充分利用空间
  - 表单：水平布局，标签右对齐
  - 表格：显示完整列，支持列固定

- **2xl** (≥ 1536px)：
  - 导航栏：固定展开
  - 内容区：三栏布局，最大化利用空间
  - 表单：水平布局，标签右对齐
  - 表格：显示完整列，支持列固定和虚拟滚动

### 11.3 页面设计

#### 11.3.1 主页面

**布局**：左侧导航 + 右侧内容区 + 顶部状态栏

**核心组件**：
- **导航菜单**：包含「数据源管理」、「代码生成」、「模板管理」、「生成历史」等菜单项
- **面包屑导航**：显示当前页面路径，便于用户定位
- **功能卡片**：展示核心功能入口，如「新建数据源」、「快速生成代码」等
- **统计数据**：显示代码生成次数、成功率等关键指标

**视觉设计**：
- 使用 BONE 设计系统的主色调 #0ea5e9
- 卡片式布局，带有轻微阴影和圆角
- 响应式设计，在小屏幕上自动调整布局

#### 11.3.2 数据源管理页面

**功能**：管理数据库连接配置

**页面结构**：
- **顶部操作栏**：新建数据源按钮
- **数据源列表**：表格形式展示已配置的数据源
- **搜索和筛选**：支持按名称、类型等筛选数据源
- **批量操作**：支持批量启用/禁用、删除数据源

**表格列**：
- 复选框（批量操作）
- 数据源名称
- 数据库类型
- 主机地址
- 端口
- 数据库名
- 状态（启用/禁用）
- 最后测试时间
- 操作（编辑、测试连接、删除）

**新增/编辑数据源弹窗**：
- 数据源名称（必填）
- 数据库类型（下拉选择：MySQL、PostgreSQL、Oracle、SQL Server）
- 主机地址（必填）
- 端口（必填）
- 数据库名（必填）
- 用户名（必填）
- 密码（必填，密码框）
- 连接参数（可选，JSON 格式）
- 测试连接按钮
- 保存按钮

#### 11.3.3 代码生成页面

**功能**：根据表结构和模板生成代码

**页面结构**：
- **数据源选择**：下拉选择已配置的数据源
- **同步表结构按钮**：点击后同步数据源中的表结构
- **表选择区域**：表格形式展示可选择的表
- **生成配置区域**：
  - 项目名称（必填）
  - 基础包路径（必填）
  - 模块名称（必填）
  - 模板选择（下拉选择）
  - 高级配置（展开/折叠）
- **生成按钮**：点击后生成代码
- **预览区域**：生成前预览代码内容

**表选择表格**：
- 复选框（多选）
- 表名
- 表注释
- 列数
- 操作（查看详情）

**同步表结构弹窗**：
- 数据源选择
- 加载状态显示
- 表结构列表（带分页）
- 全选/反选功能
- 确认同步按钮

#### 11.3.4 模板管理页面

**功能**：管理代码生成使用的模板

**页面结构**：
- **顶部操作栏**：新建模板按钮
- **模板列表**：表格形式展示可用的模板
- **搜索和筛选**：支持按名称、类型、语言等筛选模板

**表格列**：
- 模板名称
- 模板编码
- 类型
- 语言
- 模板引擎
- 版本
- 状态
- 操作（预览、编辑、删除）

**模板预览弹窗**：
- 模板名称
- 模板内容（代码编辑器）
- 示例输出（预览区域）

#### 11.3.5 生成历史页面

**功能**：查看代码生成的历史记录

**页面结构**：
- **历史记录列表**：表格形式展示生成历史
- **搜索和筛选**：支持按项目名称、状态等筛选历史
- **时间范围选择**：选择查看特定时间段的历史

**表格列**：
- 项目名称
- 基础包路径
- 模块名称
- 生成时间
- 状态（成功/失败）
- 生成文件数
- 操作（查看详情、下载代码）

**历史详情弹窗**：
- 生成配置信息
- 生成的文件列表
- 错误信息（如果失败）
- 下载按钮

### 11.4 组件设计

#### 11.4.1 基础组件

**按钮**：
- 主要按钮：使用主色调，用于核心操作
- 次要按钮：使用次要色调，用于辅助操作
- 文本按钮：无背景，用于轻量化操作
- 危险按钮：使用红色，用于删除等危险操作

**表单**：
- 表单项：标签顶对齐，必填项标记红色星号
- 输入框：支持前缀/后缀图标，实时验证
- 下拉选择：支持搜索、远程加载
- 开关：用于启用/禁用功能

**表格**：
- 支持分页、排序、筛选
- 支持列宽调整、列固定
- 支持行选择、批量操作
- 支持空状态、加载状态

**弹窗**：
- 对话框：用于需要用户确认的操作
- 抽屉：用于展示详细信息或复杂表单
- 通知：用于操作结果提示

#### 11.4.2 业务组件

**数据源测试组件**：
- 测试连接按钮
- 加载状态显示
- 测试结果反馈（成功/失败）
- 错误信息提示

**表结构同步组件**：
- 数据源选择器
- 同步进度显示
- 表结构列表（带分页）
- 全选/反选功能

**代码预览组件**：
- 代码编辑器（支持语法高亮）
- 文件切换标签
- 复制代码按钮
- 下载按钮

**模板编辑器组件**：
- 代码编辑器（支持语法高亮）
- 模板变量提示
- 预览功能
- 验证功能

### 11.5 用户流程

#### 11.5.1 数据源配置流程
1. 用户点击「新建数据源」按钮
2. 填写数据源配置信息
3. 点击「测试连接」按钮验证连接
4. 测试成功后点击「保存」按钮
5. 系统提示保存成功，数据源列表刷新

#### 11.5.2 代码生成流程
1. 用户进入代码生成页面
2. 选择数据源
3. 点击「同步表结构」按钮
4. 在弹窗中选择需要的表
5. 填写生成配置（项目名称、包路径、模块名称）
6. 选择代码模板
7. 点击「生成代码」按钮
8. 系统显示生成进度
9. 生成完成后，显示生成结果和下载按钮

#### 11.5.3 模板管理流程
1. 用户进入模板管理页面
2. 点击「新建模板」按钮
3. 填写模板信息和内容
4. 点击「预览」按钮查看效果
5. 点击「保存」按钮保存模板

### 11.6 响应式设计

**断点设置**：
- **xs** (< 640px)：单栏布局，导航折叠，内容垂直排列
- **sm** (640px-767px)：双栏布局，导航可折叠，内容开始横向排列
- **md** (768px-1023px)：双栏布局，导航展开，内容充分利用空间
- **lg** (1024px-1279px)：三栏布局，完整功能展示
- **xl** (1280px-1535px)：三栏布局，内容区最大化
- **2xl** (≥ 1536px)：三栏布局，充分利用大屏幕空间

**适配策略**：
- **移动优先**：优先设计移动设备体验，再逐步扩展到更大屏幕
- **内容优先**：确保核心功能在所有设备上都可用
- **弹性布局**：使用弹性布局和网格系统，适应不同屏幕尺寸
- **组件适配**：根据屏幕尺寸调整组件大小、布局和交互方式
- **性能优化**：针对移动设备进行性能优化，减少资源消耗

### 11.7 微前端集成

**集成方式**：
- 使用 Qiankun 微前端框架集成到 BONE 平台
- 作为独立的微应用，拥有自己的路由和状态管理
- 与主应用和其他微应用通过事件总线通信

**样式隔离**：
- 使用 CSS Modules 实现样式隔离
- 避免使用全局样式，如必须使用，需添加命名空间
- 共享 BONE 设计系统的设计令牌

**通信机制**：
- 使用增强的事件总线进行应用间通信
- 支持请求-响应模式的通信
- 通信内容加密，保护敏感信息

## 12. 总结

Studio Generator 模块作为 BONE 平台的代码生成工具，提供了基于数据库表结构的代码自动生成功能，帮助开发者快速生成符合 BONE 平台规范的代码，提高开发效率。通过合理的技术选型和实现方案，该模块能够满足企业级应用的性能和安全要求，为用户提供良好的使用体验。

**关键成功因素**：
- 支持多种数据库类型的表结构同步
- 提供灵活的代码模板和生成配置
- 支持代码预览和验证功能
- 安全、可靠的数据源管理
- 高性能、低资源消耗的实现
- 采用双模式 UseCase 策略，平衡效率与扩展性
- 支持 AI 可见性规范，实现能力可发现和可编排
- 符合 BONE-Blueprint v24.0 工程规范，确保架构正确性
- 现代化、用户友好的 UI 设计

**技术亮点**：
- **双模式 UseCase**：Mode A 直写（快速），Mode B 编排（可复用、AI 就绪）
- **AI 可见性**：通过 @Capability 注解标记能力，支持 AI 发现和编排
- **Handler Registry**：自动扫描和注册能力，提供能力元数据管理
- **Repository 空接口**：遵循规范，所有查询通过 Criteria 或 QueryBuilder 实现
- **ID 生成上移**：ID 由应用层生成，领域层纯接收
- **实体 @Table 显式绑定**：所有实体类使用 @Table 注解显式指定数据库表名
- **现代化 UI**：遵循 BONE 设计系统，提供一致、美观的用户界面
- **响应式布局**：适配不同屏幕尺寸，提供一致的跨设备体验
- **微前端集成**：使用 Qiankun 框架集成到 BONE 平台

该模块的实现将为 BONE 平台的整体开发效率提供重要支撑，是平台的重要组成部分。通过自动化代码生成，开发者可以将更多精力集中在业务逻辑的实现上，而不是重复的代码编写工作。

通过采用微服务架构和清晰的Java工程结构，Studio Generator 模块能够实现高内聚、低耦合的设计，便于独立开发、测试和部署，同时为未来的功能扩展和性能优化提供了良好的基础。

**架构演进**：
- 从简单的代码生成工具，演进为企业级的代码生成服务
- 从单一模式，演进为双模式策略，平衡效率与扩展性
- 从纯手动操作，演进为 AI 可编排的能力服务
- 从独立模块，演进为 BONE 平台的核心能力之一
- 从功能驱动，演进为用户体验驱动的现代化应用

Studio Generator 模块将持续演进，为 BONE 平台的开发效率和质量提供更强有力的支持。