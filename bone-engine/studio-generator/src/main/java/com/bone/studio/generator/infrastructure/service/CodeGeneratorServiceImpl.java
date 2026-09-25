package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.gateway.CatalogMetadataGateway;
import com.bone.studio.generator.domain.model.catalog.MetadataSourceType;
import com.bone.studio.generator.domain.model.code.CodeGenerationRequest;
import com.bone.studio.generator.domain.model.code.CodeGenerationResponse;
import com.bone.studio.generator.domain.model.code.GeneratedFile;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import com.bone.studio.generator.domain.model.data.DataSource;
import com.bone.studio.generator.domain.model.data.DatabaseTable;
import com.bone.studio.generator.domain.model.data.GenColumnMetadata;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import com.bone.studio.generator.domain.model.data.TableColumn;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import com.bone.studio.generator.domain.service.CodeGeneratorService;
import com.bone.studio.generator.domain.service.FileGenerator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class CodeGeneratorServiceImpl implements CodeGeneratorService {

  /** 内置模板类型顺序即生成顺序：响应对象先于控制器，控制器依赖它（HC-003）。 */
  private static final List<String> BUILT_IN_TEMPLATE_TYPES =
      List.of("entity", "repository", "applicationService", "response", "controller");

  private final DataSourceRepository dataSourceRepository;
  private final CatalogMetadataGateway catalogMetadataGateway;
  private final List<FileGenerator> fileGenerators;

  public CodeGeneratorServiceImpl(
      DataSourceRepository dataSourceRepository,
      CatalogMetadataGateway catalogMetadataGateway,
      List<FileGenerator> fileGenerators) {
    this.dataSourceRepository = dataSourceRepository;
    this.catalogMetadataGateway = catalogMetadataGateway;
    this.fileGenerators = fileGenerators;
  }

  @Override
  public CodeGenerationResponse generateCode(CodeGenerationRequest request) {
    String generationId = UUID.randomUUID().toString();
    List<GeneratedFile> generatedFiles = new ArrayList<>();

    try {
      // 1. 加载表结构：目录快照 或 物理库反向
      List<DatabaseTable> tables = resolveTables(request);

      // 2. 生成代码：与主链路同一套模板，避免本链路产出 extends Entity + domain/service 的不合规骨架
      int skippedRuntime = 0;
      for (DatabaseTable table : tables) {
        if (table.isRuntimeDelivery()) {
          skippedRuntime++;
          continue;
        }
        GenTableMetadata metadata = toMetadata(table);
        for (String templateType : BUILT_IN_TEMPLATE_TYPES) {
          CodeTemplate template =
              CodeTemplate.builder().code(templateType).name(templateType).build();
          for (FileGenerator generator : fileGenerators) {
            if (generator.supports(templateType)) {
              generatedFiles.add(
                  generator.generate(
                      metadata, template, request.getBasePackage(), request.getModuleName()));
              break;
            }
          }
        }
      }
      String successMsg =
          skippedRuntime > 0
              ? "代码生成成功（已跳过 " + skippedRuntime + " 个 RUNTIME 实体，请使用 /api/v1/runtime 动态 API）"
              : "代码生成成功";

      // 3. 保存生成的文件
      String outputPath =
          request.getOutputPath() != null && !request.getOutputPath().isEmpty()
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
          .message(successMsg)
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

  /** 物理库表 → 生成器领域模型：让本链路与主链路共用同一份模板上下文。 */
  private static GenTableMetadata toMetadata(DatabaseTable dbTable) {
    GenTableMetadata metadata = GenTableMetadata.create(0L, 0L, "physical", dbTable);
    List<GenColumnMetadata> columns = new ArrayList<>();
    if (dbTable.getColumns() != null) {
      for (TableColumn dbColumn : dbTable.getColumns()) {
        columns.add(GenColumnMetadata.create(0L, 0L, 0L, dbColumn));
      }
    }
    metadata.attachColumns(columns);
    return metadata;
  }

  public boolean testConnection(DataSource dataSource) {
    Connection connection = null;
    try {
      String url = buildJdbcUrl(dataSource);
      connection =
          DriverManager.getConnection(url, dataSource.getUsername(), dataSource.getPassword());
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
    DataSource dataSource =
        dataSourceRepository.findById(
            com.bone.studio.generator.common.StudioIds.parseRequired(dataSourceId));
    if (dataSource == null) {
      throw new IllegalArgumentException("数据源不存在: " + dataSourceId);
    }

    List<DatabaseTable> tables = new ArrayList<>();
    Connection connection = null;
    try {
      String url = buildJdbcUrl(dataSource);
      connection =
          DriverManager.getConnection(url, dataSource.getUsername(), dataSource.getPassword());

      // 获取表信息
      DatabaseMetaData metaData = connection.getMetaData();
      ResultSet tablesResultSet = metaData.getTables(null, null, "%", new String[] {"TABLE"});

      while (tablesResultSet.next()) {
        String tableName = tablesResultSet.getString("TABLE_NAME");
        String tableComment = tablesResultSet.getString("REMARKS");

        DatabaseTable table =
            DatabaseTable.builder()
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
        return "jdbc:mysql://"
            + dataSource.getHost()
            + ":"
            + dataSource.getPort()
            + "/"
            + dataSource.getDatabase()
            + "?useSSL=false&serverTimezone=UTC&characterEncoding=utf8&useUnicode=true&useInformationSchema=true&remarksReporting=true";
      case "postgresql":
        return "jdbc:postgresql://"
            + dataSource.getHost()
            + ":"
            + dataSource.getPort()
            + "/"
            + dataSource.getDatabase();
      default:
        throw new IllegalArgumentException("不支持的数据库类型: " + dataSource.getType());
    }
  }

  private List<TableColumn> loadColumns(Connection connection, String tableName)
      throws SQLException {
    List<TableColumn> columns = new ArrayList<>();
    DatabaseMetaData metaData = connection.getMetaData();
    ResultSet columnsResultSet = metaData.getColumns(null, null, tableName, "%");

    while (columnsResultSet.next()) {
      String columnName = columnsResultSet.getString("COLUMN_NAME");
      String columnType = columnsResultSet.getString("TYPE_NAME");
      String columnComment = columnsResultSet.getString("REMARKS");
      boolean nullable = columnsResultSet.getInt("NULLABLE") == 1;
      String defaultValue = columnsResultSet.getString("COLUMN_DEF");
      int jdbcType = columnsResultSet.getInt("DATA_TYPE");
      int length = columnsResultSet.getInt("COLUMN_SIZE");
      int precision = columnsResultSet.getInt("DECIMAL_DIGITS");

      TableColumn column =
          TableColumn.builder()
              .columnName(columnName)
              .jdbcType(jdbcType)
              .columnType(columnType)
              .columnSize(length)
              .decimalDigits(precision)
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

    // 更新主键信息（原先只改循环变量引用，没写回列表，主键标记永远是 false）
    String primaryKey = loadPrimaryKey(connection, tableName);
    if (primaryKey != null) {
      for (int i = 0; i < columns.size(); i++) {
        TableColumn column = columns.get(i);
        if (column.getColumnName().equals(primaryKey)) {
          columns.set(
              i,
              TableColumn.builder()
                  .columnName(column.getColumnName())
                  .jdbcType(column.getJdbcType())
                  .columnType(column.getColumnType())
                  .columnSize(column.getColumnSize())
                  .decimalDigits(column.getDecimalDigits())
                  .columnComment(column.getColumnComment())
                  .nullable(false)
                  .primaryKey(true)
                  .defaultValue(column.getDefaultValue())
                  .length(column.getLength())
                  .precision(column.getPrecision())
                  .scale(column.getScale())
                  .build());
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
}
