package com.bone.metadata.engine.runtime.query;

import com.bone.metadata.engine.runtime.query.ast.QueryAst;
import org.springframework.stereotype.Component;

/** SmartQL查询解析器，用于将SmartQL查询字符串解析为查询抽象语法树 */
@Component
public class SmartQLParser {

  /**
   * 解析SmartQL查询字符串
   *
   * @param smartql SmartQL查询字符串
   * @return 查询抽象语法树
   */
  public QueryAst parse(String smartql) {
    QueryAst queryAst = new QueryAst();

    // 简单实现：这里只是一个基础的解析器，实际项目中需要更复杂的解析逻辑
    // 这里假设查询格式类似于: SELECT field1, field2 FROM entity WHERE condition

    try {
      String normalizedQuery = smartql.trim().toUpperCase();

      // 提取实体名称（简单实现，实际需要更复杂的解析）
      if (normalizedQuery.contains("FROM")) {
        int fromIndex = normalizedQuery.indexOf("FROM") + 4;
        int nextSpace = normalizedQuery.indexOf(" ", fromIndex);
        int whereIndex = normalizedQuery.indexOf("WHERE");

        int endIndex =
            whereIndex > 0 ? whereIndex : (nextSpace > 0 ? nextSpace : normalizedQuery.length());
        String objectName = smartql.substring(fromIndex, endIndex).trim();
        queryAst.setObjectName(objectName);
      }

      // 提取选择的字段（简单实现）
      if (normalizedQuery.contains("SELECT")) {
        int selectIndex = 6; // "SELECT".length()
        int fromIndex = normalizedQuery.indexOf("FROM");

        if (fromIndex > selectIndex) {
          String fieldsStr = smartql.substring(selectIndex, fromIndex).trim();
          String[] fields = fieldsStr.split(",");
          for (String field : fields) {
            queryAst.addSelectField(field.trim());
          }
        }
      }

      // 提取参数（简单实现，实际需要更复杂的解析）
      // 这里假设参数格式为 :paramName
      int paramIndex = smartql.indexOf(":");
      while (paramIndex >= 0) {
        int nextSpace = smartql.indexOf(" ", paramIndex);
        int nextComma = smartql.indexOf(",", paramIndex);
        int nextParen = smartql.indexOf(")", paramIndex);
        int nextOperator =
            Math.min(
                Math.min(
                    nextSpace > 0 ? nextSpace : Integer.MAX_VALUE,
                    nextComma > 0 ? nextComma : Integer.MAX_VALUE),
                nextParen > 0 ? nextParen : Integer.MAX_VALUE);

        int endIndex = nextOperator < Integer.MAX_VALUE ? nextOperator : smartql.length();
        String paramName = smartql.substring(paramIndex + 1, endIndex).trim();
        queryAst.addParameter(paramName);

        paramIndex = smartql.indexOf(":", paramIndex + 1);
      }

      // 设置默认值
      queryAst.setContainsDynamicFunctions(false);
      queryAst.setHasComplexConditions(false);

    } catch (Exception e) {
      throw new RuntimeException("解析SmartQL查询失败: " + smartql, e);
    }

    return queryAst;
  }
}
