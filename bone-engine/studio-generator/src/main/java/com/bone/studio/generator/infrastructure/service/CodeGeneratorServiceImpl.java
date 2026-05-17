package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.code.CodeGenerationRequest;
import com.bone.studio.generator.domain.code.CodeGenerationResponse;
import com.bone.studio.generator.domain.code.GeneratedFile;
import com.bone.studio.generator.domain.data.DataSource;
import com.bone.studio.generator.domain.data.DatabaseTable;
import com.bone.studio.generator.domain.data.TableColumn;
import com.bone.studio.generator.domain.catalog.MetadataSourceType;
import com.bone.studio.generator.domain.gateway.CatalogMetadataGateway;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import com.bone.studio.generator.domain.service.CodeGeneratorService;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CodeGeneratorServiceImpl implements CodeGeneratorService {

    private final DataSourceRepository dataSourceRepository;
    private final CatalogMetadataGateway catalogMetadataGateway;

    public CodeGeneratorServiceImpl(
            DataSourceRepository dataSourceRepository,
            CatalogMetadataGateway catalogMetadataGateway) {
        this.dataSourceRepository = dataSourceRepository;
        this.catalogMetadataGateway = catalogMetadataGateway;
    }

    @Override
    public CodeGenerationResponse generateCode(CodeGenerationRequest request) {
        String generationId = UUID.randomUUID().toString();
        List<GeneratedFile> generatedFiles = new ArrayList<>();
        
        try {
            // 1. 加载表结构：目录快照 或 物理库反向
            List<DatabaseTable> tables = resolveTables(request);
            
            // 2. 生成代码
            for (DatabaseTable table : tables) {
                generatedFiles.addAll(generateEntity(table, request));
                generatedFiles.addAll(generateRepository(table, request));
                generatedFiles.addAll(generateService(table, request));
                generatedFiles.addAll(generateController(table, request));
                generatedFiles.addAll(generateDTOs(table, request));
            }
            
            // 3. 保存生成的文件
            String outputPath = request.getOutputPath() != null && !request.getOutputPath().isEmpty() 
                    ? request.getOutputPath() 
                    : System.getProperty("user.dir") + "/generated-code";
            
            Path outputDir = Paths.get(outputPath);
            if (!Files.exists(outputDir)) {
                Files.createDirectories(outputDir);
            }
            
            for (GeneratedFile file : generatedFiles) {
                Path filePath = outputDir.resolve(file.getFilePath());
                if (!Files.exists(filePath.getParent())) {
                    Files.createDirectories(filePath.getParent());
                }
                Files.write(filePath, file.getContent().getBytes(StandardCharsets.UTF_8));
            }
            
            return CodeGenerationResponse.builder()
                    .generationId(generationId)
                    .status("SUCCESS")
                    .message("代码生成成功")
                    .generatedFiles(generatedFiles)
                    .executionTime(0)
                    .outputPath(outputPath)
                    .build();
            
        } catch (Exception e) {
            return CodeGenerationResponse.builder()
                    .generationId(generationId)
                    .status("FAILED")
                    .message("代码生成失败: " + e.getMessage())
                    .generatedFiles(generatedFiles)
                    .executionTime(0)
                    .outputPath("")
                    .build();
        }
    }

    @Override
    public boolean testConnection(DataSource dataSource) {
        Connection connection = null;
        try {
            String url = buildJdbcUrl(dataSource);
            connection = DriverManager.getConnection(url, dataSource.getUsername(), dataSource.getPassword());
            return connection != null && !connection.isClosed();
        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    // Ignore
                }
            }
        }
    }

    @Override
    public List<DatabaseTable> loadCatalogTables(Long tenantId, List<String> entityCodes) {
        return catalogMetadataGateway.loadPublishedSnapshots(tenantId, entityCodes);
    }

    private List<DatabaseTable> resolveTables(CodeGenerationRequest request) {
        if (request.getMetadataSource() == MetadataSourceType.CATALOG_SNAPSHOT) {
            List<DatabaseTable> tables =
                    catalogMetadataGateway.loadPublishedSnapshots(
                            request.getTenantId(), request.getEntityCodes());
            if (request.getEntityCodes() != null && !request.getEntityCodes().isEmpty()) {
                return tables;
            }
            if (request.getTableNames() != null && !request.getTableNames().isEmpty()) {
                return tables.stream()
                        .filter(t -> request.getTableNames().contains(t.getTableName()))
                        .collect(Collectors.toList());
            }
            return tables;
        }
        List<DatabaseTable> tables = new ArrayList<>();
        if (request.getDataSourceId() != null && !request.getDataSourceId().isEmpty()) {
            tables = loadTables(request.getDataSourceId());
            if (request.getTableNames() != null && !request.getTableNames().isEmpty()) {
                tables =
                        tables.stream()
                                .filter(table -> request.getTableNames().contains(table.getTableName()))
                                .collect(Collectors.toList());
            }
        }
        return tables;
    }

    @Override
    public List<DatabaseTable> loadTables(String dataSourceId) {
        DataSource dataSource = dataSourceRepository.findById(
                com.bone.studio.generator.common.StudioIds.parseRequired(dataSourceId));
        if (dataSource == null) {
            throw new IllegalArgumentException("数据源不存在: " + dataSourceId);
        }
        
        List<DatabaseTable> tables = new ArrayList<>();
        Connection connection = null;
        try {
            String url = buildJdbcUrl(dataSource);
            connection = DriverManager.getConnection(url, dataSource.getUsername(), dataSource.getPassword());
            
            // 获取表信息
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet tablesResultSet = metaData.getTables(null, null, "%", new String[]{"TABLE"});
            
            while (tablesResultSet.next()) {
                String tableName = tablesResultSet.getString("TABLE_NAME");
                String tableComment = tablesResultSet.getString("REMARKS");
                
                DatabaseTable table = DatabaseTable.builder()
                        .tableName(tableName)
                        .tableComment(tableComment)
                        .columns(loadColumns(connection, tableName))
                        .primaryKey(loadPrimaryKey(connection, tableName))
                        .indexes(loadIndexes(connection, tableName))
                        .build();
                
                tables.add(table);
            }
            
        } catch (Exception e) {
            throw new RuntimeException("加载表结构失败: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    // Ignore
                }
            }
        }
        
        return tables;
    }

    private String buildJdbcUrl(DataSource dataSource) {
        switch (dataSource.getType()) {
            case "mysql":
                return "jdbc:mysql://" + dataSource.getHost() + ":" + dataSource.getPort() + "/" + dataSource.getDatabase() + "?useSSL=false&serverTimezone=UTC";
            case "postgresql":
                return "jdbc:postgresql://" + dataSource.getHost() + ":" + dataSource.getPort() + "/" + dataSource.getDatabase();
            default:
                throw new IllegalArgumentException("不支持的数据库类型: " + dataSource.getType());
        }
    }

    private List<TableColumn> loadColumns(Connection connection, String tableName) throws SQLException {
        List<TableColumn> columns = new ArrayList<>();
        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet columnsResultSet = metaData.getColumns(null, null, tableName, "%");
        
        while (columnsResultSet.next()) {
            String columnName = columnsResultSet.getString("COLUMN_NAME");
            String columnType = columnsResultSet.getString("TYPE_NAME");
            String columnComment = columnsResultSet.getString("REMARKS");
            boolean nullable = columnsResultSet.getInt("NULLABLE") == 1;
            String defaultValue = columnsResultSet.getString("COLUMN_DEF");
            int length = columnsResultSet.getInt("COLUMN_SIZE");
            int precision = columnsResultSet.getInt("DECIMAL_DIGITS");
            
            TableColumn column = TableColumn.builder()
                    .columnName(columnName)
                    .columnType(columnType)
                    .columnComment(columnComment)
                    .nullable(nullable)
                    .primaryKey(false) // 后续会更新
                    .defaultValue(defaultValue)
                    .length(length)
                    .precision(precision)
                    .scale(precision)
                    .build();
            
            columns.add(column);
        }
        
        // 更新主键信息
        String primaryKey = loadPrimaryKey(connection, tableName);
        if (primaryKey != null) {
            for (TableColumn column : columns) {
                if (column.getColumnName().equals(primaryKey)) {
                    column = TableColumn.builder()
                            .columnName(column.getColumnName())
                            .columnType(column.getColumnType())
                            .columnComment(column.getColumnComment())
                            .nullable(false)
                            .primaryKey(true)
                            .defaultValue(column.getDefaultValue())
                            .length(column.getLength())
                            .precision(column.getPrecision())
                            .scale(column.getScale())
                            .build();
                }
            }
        }
        
        return columns;
    }

    private String loadPrimaryKey(Connection connection, String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet primaryKeysResultSet = metaData.getPrimaryKeys(null, null, tableName);
        if (primaryKeysResultSet.next()) {
            return primaryKeysResultSet.getString("COLUMN_NAME");
        }
        return null;
    }

    private List<String> loadIndexes(Connection connection, String tableName) throws SQLException {
        List<String> indexes = new ArrayList<>();
        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet indexesResultSet = metaData.getIndexInfo(null, null, tableName, false, false);
        
        while (indexesResultSet.next()) {
            String indexName = indexesResultSet.getString("INDEX_NAME");
            if (indexName != null && !indexName.equals("PRIMARY")) {
                indexes.add(indexName);
            }
        }
        
        return indexes;
    }

    private List<GeneratedFile> generateEntity(DatabaseTable table, CodeGenerationRequest request) {
        List<GeneratedFile> files = new ArrayList<>();
        String packageName = buildPackageName(request, "domain.model.entity");
        String className = toCamelCase(table.getTableName(), true);
        
        StringBuilder content = new StringBuilder();
        content.append("package " + packageName + ";\n\n");
        content.append("import lombok.Getter;\n");
        content.append("import lombok.NoArgsConstructor;\n");
        content.append("import com.bone.core.domain.Entity;\n\n");
        content.append("@Getter\n");
        content.append("@NoArgsConstructor\n");
        content.append("public class " + className + " extends Entity<Long> {\n\n");
        
        for (TableColumn column : table.getColumns()) {
            String fieldName = toCamelCase(column.getColumnName(), false);
            String fieldType = mapColumnTypeToJavaType(column.getColumnType());
            content.append("    private " + fieldType + " " + fieldName + ";\n");
        }
        
        content.append("\n");
        content.append("    // 构造方法和业务方法\n");
        content.append("}\n");
        
        String filePath = packageName.replace('.', '/') + "/" + className + ".java";
        files.add(GeneratedFile.builder()
                .fileName(className + ".java")
                .filePath(filePath)
                .content(content.toString())
                .fileType("java")
                .fileSize(content.length())
                .build());
        
        return files;
    }

    private List<GeneratedFile> generateRepository(DatabaseTable table, CodeGenerationRequest request) {
        List<GeneratedFile> files = new ArrayList<>();
        String packageName = buildPackageName(request, "domain.repository");
        String className = toCamelCase(table.getTableName(), true) + "Repository";
        
        StringBuilder content = new StringBuilder();
        content.append("package " + packageName + ";\n\n");
        content.append("import com.bone.metadata.sdk.domain.repository.Repository;\n");
        content.append("import " + buildPackageName(request, "domain.model.entity") + "." + toCamelCase(table.getTableName(), true) + ";\n\n");
        content.append("public interface " + className + " extends Repository<" + toCamelCase(table.getTableName(), true) + ", Long> {\n");
        content.append("}\n");
        
        String filePath = packageName.replace('.', '/') + "/" + className + ".java";
        files.add(GeneratedFile.builder()
                .fileName(className + ".java")
                .filePath(filePath)
                .content(content.toString())
                .fileType("java")
                .fileSize(content.length())
                .build());
        
        return files;
    }

    private List<GeneratedFile> generateService(DatabaseTable table, CodeGenerationRequest request) {
        List<GeneratedFile> files = new ArrayList<>();
        String packageName = buildPackageName(request, "domain.service");
        String className = toCamelCase(table.getTableName(), true) + "Service";
        
        StringBuilder content = new StringBuilder();
        content.append("package " + packageName + ";\n\n");
        content.append("import " + buildPackageName(request, "domain.model.entity") + "." + toCamelCase(table.getTableName(), true) + ";\n");
        content.append("import java.util.List;\n\n");
        content.append("public interface " + className + " {\n");
        content.append("    " + toCamelCase(table.getTableName(), true) + " create(" + toCamelCase(table.getTableName(), true) + " entity);\n");
        content.append("    " + toCamelCase(table.getTableName(), true) + " update(" + toCamelCase(table.getTableName(), true) + " entity);\n");
        content.append("    void delete(Long id);\n");
        content.append("    " + toCamelCase(table.getTableName(), true) + " findById(Long id);\n");
        content.append("    List<" + toCamelCase(table.getTableName(), true) + "> findAll();\n");
        content.append("}\n");
        
        String filePath = packageName.replace('.', '/') + "/" + className + ".java";
        files.add(GeneratedFile.builder()
                .fileName(className + ".java")
                .filePath(filePath)
                .content(content.toString())
                .fileType("java")
                .fileSize(content.length())
                .build());
        
        return files;
    }

    private List<GeneratedFile> generateController(DatabaseTable table, CodeGenerationRequest request) {
        List<GeneratedFile> files = new ArrayList<>();
        String packageName = buildPackageName(request, "adapter.web.controller");
        String className = toCamelCase(table.getTableName(), true) + "Controller";
        
        StringBuilder content = new StringBuilder();
        content.append("package " + packageName + ";\n\n");
        content.append("import org.springframework.web.bind.annotation.*;\n");
        content.append("import " + buildPackageName(request, "domain.service") + "." + toCamelCase(table.getTableName(), true) + "Service;\n");
        content.append("import " + buildPackageName(request, "domain.model.entity") + "." + toCamelCase(table.getTableName(), true) + ";\n");
        content.append("import java.util.List;\n\n");
        content.append("@RestController\n");
        content.append("@RequestMapping(\"/api/" + table.getTableName().toLowerCase() + ")\n");
        content.append("public class " + className + " {\n\n");
        content.append("    private final " + toCamelCase(table.getTableName(), true) + "Service service;\n\n");
        content.append("    public " + className + "(" + toCamelCase(table.getTableName(), true) + "Service service) {\n");
        content.append("        this.service = service;\n");
        content.append("    }\n\n");
        content.append("    @PostMapping\n");
        content.append("    public " + toCamelCase(table.getTableName(), true) + " create(@RequestBody " + toCamelCase(table.getTableName(), true) + " entity) {\n");
        content.append("        return service.create(entity);\n");
        content.append("    }\n\n");
        content.append("    @PutMapping(\"/{id}\")\n");
        content.append("    public " + toCamelCase(table.getTableName(), true) + " update(@PathVariable Long id, @RequestBody " + toCamelCase(table.getTableName(), true) + " entity) {\n");
        content.append("        return service.update(entity);\n");
        content.append("    }\n\n");
        content.append("    @DeleteMapping(\"/{id}\")\n");
        content.append("    public void delete(@PathVariable Long id) {\n");
        content.append("        service.delete(id);\n");
        content.append("    }\n\n");
        content.append("    @GetMapping(\"/{id}\")\n");
        content.append("    public " + toCamelCase(table.getTableName(), true) + " findById(@PathVariable Long id) {\n");
        content.append("        return service.findById(id);\n");
        content.append("    }\n\n");
        content.append("    @GetMapping\n");
        content.append("    public List<" + toCamelCase(table.getTableName(), true) + "> findAll() {\n");
        content.append("        return service.findAll();\n");
        content.append("    }\n");
        content.append("}\n");
        
        String filePath = packageName.replace('.', '/') + "/" + className + ".java";
        files.add(GeneratedFile.builder()
                .fileName(className + ".java")
                .filePath(filePath)
                .content(content.toString())
                .fileType("java")
                .fileSize(content.length())
                .build());
        
        return files;
    }

    private List<GeneratedFile> generateDTOs(DatabaseTable table, CodeGenerationRequest request) {
        List<GeneratedFile> files = new ArrayList<>();
        String packageName = buildPackageName(request, "adapter.web.dto");
        
        // Create Request DTO
        String createRequestClassName = toCamelCase(table.getTableName(), true) + "CreateRequest";
        StringBuilder createRequestContent = new StringBuilder();
        createRequestContent.append("package " + packageName + ".request;\n\n");
        createRequestContent.append("import lombok.Data;\n\n");
        createRequestContent.append("@Data\n");
        createRequestContent.append("public class " + createRequestClassName + " {\n");
        
        for (TableColumn column : table.getColumns()) {
            if (!column.isPrimaryKey()) {
                String fieldName = toCamelCase(column.getColumnName(), false);
                String fieldType = mapColumnTypeToJavaType(column.getColumnType());
                createRequestContent.append("    private " + fieldType + " " + fieldName + ";\n");
            }
        }
        createRequestContent.append("}\n");
        
        String createRequestFilePath = packageName.replace('.', '/') + "/request/" + createRequestClassName + ".java";
        files.add(GeneratedFile.builder()
                .fileName(createRequestClassName + ".java")
                .filePath(createRequestFilePath)
                .content(createRequestContent.toString())
                .fileType("java")
                .fileSize(createRequestContent.length())
                .build());
        
        // Update Request DTO
        String updateRequestClassName = toCamelCase(table.getTableName(), true) + "UpdateRequest";
        StringBuilder updateRequestContent = new StringBuilder();
        updateRequestContent.append("package " + packageName + ".request;\n\n");
        updateRequestContent.append("import lombok.Data;\n\n");
        updateRequestContent.append("@Data\n");
        updateRequestContent.append("public class " + updateRequestClassName + " {\n");
        
        for (TableColumn column : table.getColumns()) {
            String fieldName = toCamelCase(column.getColumnName(), false);
            String fieldType = mapColumnTypeToJavaType(column.getColumnType());
            updateRequestContent.append("    private " + fieldType + " " + fieldName + ";\n");
        }
        updateRequestContent.append("}\n");
        
        String updateRequestFilePath = packageName.replace('.', '/') + "/request/" + updateRequestClassName + ".java";
        files.add(GeneratedFile.builder()
                .fileName(updateRequestClassName + ".java")
                .filePath(updateRequestFilePath)
                .content(updateRequestContent.toString())
                .fileType("java")
                .fileSize(updateRequestContent.length())
                .build());
        
        // Response DTO
        String responseClassName = toCamelCase(table.getTableName(), true) + "Response";
        StringBuilder responseContent = new StringBuilder();
        responseContent.append("package " + packageName + ".response;\n\n");
        responseContent.append("import lombok.Data;\n\n");
        responseContent.append("@Data\n");
        responseContent.append("public class " + responseClassName + " {\n");
        
        for (TableColumn column : table.getColumns()) {
            String fieldName = toCamelCase(column.getColumnName(), false);
            String fieldType = mapColumnTypeToJavaType(column.getColumnType());
            responseContent.append("    private " + fieldType + " " + fieldName + ";\n");
        }
        responseContent.append("}\n");
        
        String responseFilePath = packageName.replace('.', '/') + "/response/" + responseClassName + ".java";
        files.add(GeneratedFile.builder()
                .fileName(responseClassName + ".java")
                .filePath(responseFilePath)
                .content(responseContent.toString())
                .fileType("java")
                .fileSize(responseContent.length())
                .build());
        
        return files;
    }

    private String buildPackageName(CodeGenerationRequest request, String suffix) {
        String basePackage = request.getBasePackage() != null && !request.getBasePackage().isEmpty() 
                ? request.getBasePackage() 
                : "com.example";
        
        if (request.getModuleName() != null && !request.getModuleName().isEmpty()) {
            return basePackage + "." + request.getModuleName() + "." + suffix;
        }
        
        return basePackage + "." + suffix;
    }

    private String toCamelCase(String str, boolean capitalizeFirst) {
        String[] parts = str.split("_|");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (i == 0 && !capitalizeFirst) {
                result.append(part.toLowerCase());
            } else {
                result.append(Character.toUpperCase(part.charAt(0)));
                result.append(part.substring(1).toLowerCase());
            }
        }
        
        return result.toString();
    }

    private String mapColumnTypeToJavaType(String columnType) {
        columnType = columnType.toLowerCase();
        
        switch (columnType) {
            case "int":
            case "integer":
                return "Integer";
            case "bigint":
                return "Long";
            case "varchar":
            case "char":
            case "text":
                return "String";
            case "date":
                return "LocalDate";
            case "datetime":
            case "timestamp":
                return "LocalDateTime";
            case "decimal":
            case "numeric":
                return "BigDecimal";
            case "boolean":
                return "Boolean";
            case "double":
                return "Double";
            case "float":
                return "Float";
            case "string":
                return "String";
            case "long":
                return "Long";
            default:
                return "String";
        }
    }
}