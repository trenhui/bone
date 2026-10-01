package com.bone.studio.generator.application;

import com.bone.core.capability.Capability;
import com.bone.studio.generator.application.query.qry.LoadTablesQuery;
import com.bone.studio.generator.domain.model.data.DatabaseTable;
import com.bone.studio.generator.domain.model.data.TableColumn;
import com.bone.studio.generator.domain.service.CodeGeneratorService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Capability(name = "loadTables", description = "加载数据源表结构", inputSchema = "{}", outputSchema = "{}")
public class LoadTablesApplicationService {

  /** 未指定 limit 时的兜底上限：真实库常有几千张表，全量回传会把浏览器打满。 */
  private static final int DEFAULT_LIMIT = 200;

  private final CodeGeneratorService codeGeneratorService;

  public LoadTablesApplicationService(CodeGeneratorService codeGeneratorService) {
    this.codeGeneratorService = codeGeneratorService;
  }

  /**
   * 物理库表发现。
   *
   * <p><b>为何要在服务端收口过滤</b>：JDBC {@code DatabaseMetaData} 只能整库扫，返回后再在前端过滤意味着
   * 一次请求要把上千张表的列明细全部序列化并推给浏览器——实测 bone 库 818 张表，报文十几 MB、前端 Select 渲染直接卡死。关键字与上限在这里做，报文按实际需要裁剪。
   */
  @Transactional(readOnly = true)
  public List<DatabaseTable> handle(LoadTablesQuery query) {
    List<DatabaseTable> tables = codeGeneratorService.loadTables(query.getDataSourceId());
    if (tables == null || tables.isEmpty()) {
      return List.of();
    }
    String keyword = query.getKeyword();
    boolean filtered = keyword != null && !keyword.isBlank();
    String needle = filtered ? keyword.trim().toLowerCase(Locale.ROOT) : null;
    int limit =
        query.getLimit() == null || query.getLimit() <= 0 ? DEFAULT_LIMIT : query.getLimit();
    boolean includeColumns = query.getIncludeColumns() == null || query.getIncludeColumns();

    List<DatabaseTable> result = new ArrayList<>();
    for (DatabaseTable table : tables) {
      if (filtered && !matches(table, needle)) {
        continue;
      }
      result.add(includeColumns ? table : withoutColumns(table));
      if (result.size() >= limit) {
        break;
      }
    }
    return List.copyOf(result);
  }

  private static boolean matches(DatabaseTable table, String needle) {
    return containsIgnoreCase(table.getTableName(), needle)
        || containsIgnoreCase(table.getTableComment(), needle);
  }

  private static boolean containsIgnoreCase(String value, String needle) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
  }

  /** 只保留表级信息：表选择器不需要列，去掉可显著缩小报文。 */
  private static DatabaseTable withoutColumns(DatabaseTable table) {
    List<TableColumn> empty = List.of();
    return DatabaseTable.builder()
        .tableName(table.getTableName())
        .tableComment(table.getTableComment())
        .columns(empty)
        .primaryKey(table.getPrimaryKey())
        .indexes(table.getIndexes())
        .deliveryMode(table.getDeliveryMode())
        .build();
  }
}
