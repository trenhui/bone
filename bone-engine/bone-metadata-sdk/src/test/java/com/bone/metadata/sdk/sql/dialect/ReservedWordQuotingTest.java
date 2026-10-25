package com.bone.metadata.sdk.sql.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.annotation.Id;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.query.BatchCompiledQuery;
import com.bone.metadata.sdk.domain.spec.TableMetadataResolver;
import com.bone.metadata.sdk.query.builder.BatchInsertBuilder;
import com.bone.metadata.sdk.query.context.BatchInsertContext;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * MySQL 保留字列名必须转义：{@code int_flow_connection.condition} 未加引号时 {@code INSERT} 抛 errno 1064 ⇒ 接口
 * 500（全模块联调实测：创建带连线的集成流程 500）。
 *
 * <p><b>为什么必须有这条测试</b>：修复提交 63e91704f 只改了实现没带测试，之后 fat-jar 未重建 ⇒ 线上现象 与代码状态脱节、回归也无法被 CI
 * 拦住。本测试把「标识符转义」钉成可执行契约。
 */
class ReservedWordQuotingTest {

  /** 复刻生产实体 {@code int_flow_connection}：{@code condition} 是 MySQL 保留字。 */
  @Table("int_flow_connection")
  static class FlowConnectionFixture {
    @Id
    @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
    private Long id;

    private Long flowId;
    private String condition;

    void setId(Long id) {
      this.id = id;
    }

    void setFlowId(Long flowId) {
      this.flowId = flowId;
    }

    void setCondition(String condition) {
      this.condition = condition;
    }
  }

  @Test
  void quoteIdentifier_wrapsWithBackticksOnMysqlAndH2() {
    DatabaseDialect dialect = new DatabaseDialect();
    assertEquals("`condition`", dialect.quoteIdentifier("condition"));
    assertEquals("`int_flow_connection`", dialect.quoteIdentifier("int_flow_connection"));
  }

  @Test
  void batchInsert_quotesReservedWordColumn() {
    var table = TableMetadataResolver.load(FlowConnectionFixture.class);
    var entity = new FlowConnectionFixture();
    entity.setId(1L);
    entity.setFlowId(2L);
    entity.setCondition("true");

    BatchCompiledQuery query =
        new BatchInsertBuilder(new DatabaseDialect())
            .build(new BatchInsertContext(table, List.of(entity)));

    String sql = query.getSql();
    assertTrue(sql.contains("`condition`"), "保留字列必须转义，否则 MySQL errno 1064 ⇒ 接口 500。实际 SQL: " + sql);
    // 变异判据：转义被摘掉后，列名会以裸 "condition" 出现在列清单里（前接空格或逗号）
    assertFalse(
        sql.matches("(?s).*[\\s(]condition[,\\s)].*"),
        "出现了未转义的裸保留字列 condition，转义被回退了。实际 SQL: " + sql);
  }
}
