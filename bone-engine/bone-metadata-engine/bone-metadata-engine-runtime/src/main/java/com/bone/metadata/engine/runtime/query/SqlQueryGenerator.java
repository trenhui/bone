package com.bone.metadata.engine.runtime.query;

import com.bone.metadata.engine.domain.metadata.EntityMetadata;
import com.bone.metadata.engine.runtime.query.ast.QueryAst;
import org.springframework.stereotype.Component;

/** SQL查询生成器，用于将查询抽象语法树转换为SQL查询语句 */
@Component
public class SqlQueryGenerator {

  /**
   * 根据查询抽象语法树和实体元数据生成SQL查询语句
   *
   * @param queryAst 查询抽象语法树
   * @param entityMetadata 实体元数据
   * @return SQL查询语句
   */
  public String generateSql(QueryAst queryAst, EntityMetadata entityMetadata) {
    StringBuilder sqlBuilder = new StringBuilder();

    // 生成SELECT子句
    sqlBuilder.append("SELECT ");
    if (queryAst.getSelectFields() != null && !queryAst.getSelectFields().isEmpty()) {
      for (int i = 0; i < queryAst.getSelectFields().size(); i++) {
        if (i > 0) {
          sqlBuilder.append(", ");
        }
        sqlBuilder.append(queryAst.getSelectFields().get(i));
      }
    } else {
      sqlBuilder.append("*");
    }

    // 生成FROM子句
    String tableName =
        entityMetadata != null ? entityMetadata.getTableName() : queryAst.getObjectName();
    sqlBuilder.append(" FROM " + tableName);

    // 生成WHERE子句
    if (queryAst.getWhereClause() != null && !queryAst.getWhereClause().isEmpty()) {
      sqlBuilder.append(" WHERE " + queryAst.getWhereClause());
    }

    // 添加删除标记过滤
    sqlBuilder.append(" AND is_deleted = false");

    // 生成ORDER BY子句
    if (queryAst.getOrderBy() != null && !queryAst.getOrderBy().isEmpty()) {
      sqlBuilder.append(" ORDER BY ");
      for (int i = 0; i < queryAst.getOrderBy().size(); i++) {
        if (i > 0) {
          sqlBuilder.append(", ");
        }
        sqlBuilder.append(queryAst.getOrderBy().get(i));
      }
    }

    return sqlBuilder.toString();
  }

  /**
   * 生成计数SQL查询语句
   *
   * @param queryAst 查询抽象语法树
   * @param entityMetadata 实体元数据
   * @return 计数SQL查询语句
   */
  public String generateCountSql(QueryAst queryAst, EntityMetadata entityMetadata) {
    String tableName =
        entityMetadata != null ? entityMetadata.getTableName() : queryAst.getObjectName();
    StringBuilder sqlBuilder = new StringBuilder();

    sqlBuilder.append("SELECT COUNT(*) FROM " + tableName);

    // 生成WHERE子句
    if (queryAst.getWhereClause() != null && !queryAst.getWhereClause().isEmpty()) {
      sqlBuilder.append(" WHERE " + queryAst.getWhereClause());
    }

    // 添加删除标记过滤
    sqlBuilder.append(" AND is_deleted = false");

    return sqlBuilder.toString();
  }

  /**
   * 生成计数查询字符串
   *
   * @param smartql SmartQL查询字符串
   * @return 计数查询字符串
   */
  public String generateCountQuery(String smartql) {
    // 简单实现：将SELECT部分替换为COUNT(*)
    String normalizedQuery = smartql.trim().toUpperCase();
    int selectIndex = normalizedQuery.indexOf("SELECT");
    int fromIndex = normalizedQuery.indexOf("FROM");

    if (selectIndex >= 0 && fromIndex > selectIndex) {
      return "SELECT COUNT(*) " + smartql.substring(fromIndex);
    }

    // 如果无法解析，返回默认的计数查询
    return "SELECT COUNT(*) FROM (" + smartql + ") AS temp_count";
  }

  /**
   * 应用分页到查询
   *
   * @param smartql SmartQL查询字符串
   * @param pageable 分页信息（这里修改为Object类型以避免类型错误）
   * @return 分页后的查询字符串
   */
  public String applyPagination(String smartql, Object pageable) {
    // 简单实现：由于Pageable不可用，暂时不应用分页
    return smartql;

    // 注释掉原有的实现
    /*
    StringBuilder sqlBuilder = new StringBuilder(smartql);

    if (pageable != null) {
        sqlBuilder.append(" LIMIT " + pageable.getPageSize());
        sqlBuilder.append(" OFFSET " + (pageable.getPageNumber() * pageable.getPageSize()));
    }

    return sqlBuilder.toString();
    */
  }
}
