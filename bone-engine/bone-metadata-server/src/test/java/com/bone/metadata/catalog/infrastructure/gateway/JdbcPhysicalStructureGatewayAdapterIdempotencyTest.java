package com.bone.metadata.catalog.infrastructure.gateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.bone.metadata.catalog.domain.model.meta.MetaField;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 物理结构对齐的**幂等契约**（P1-11，2026-10-05）。
 *
 * <p>本模块的 DDL 生成此前**零测试覆盖**（这正是设计诊断 P1-12 记录的缺口之一），所以这里用反射 直接锁住两条最容易被后人改坏的性质：
 *
 * <ol>
 *   <li>建表语句必须带 {@code IF NOT EXISTS} 且显式声明 {@code ENGINE=InnoDB DEFAULT CHARSET=utf8mb4} ——
 *       前者把幂等下沉到数据库约束（消掉「先查后建」的 TOCTOU 窗口），后者避免建出的表跟随 库默认字符集、导致后续 {@code information_schema}
 *       类型比对持续误报「类型漂移」；
 *   <li>并发下另一个执行者已加好列时抛出的错误必须被判为「已达成」而不是失败。
 * </ol>
 *
 * <p>用反射而非暴露包级方法：{@code buildCreateTable} 是纯函数（表名 + 字段列表 ⇒ DDL 文本）， 为它放宽可见性会把生产 API
 * 面扩大，而这里要的只是锁住文本契约。
 *
 * <p><b>为什么是「文本断言 + mock 执行」而不是真跑一个数据库</b>（2026-10-05 实测）： 本类要验的 DDL 带 {@code ENGINE=InnoDB DEFAULT
 * CHARSET=utf8mb4}，而 H2 连这一句都报语法错误 （实测：{@code Syntax error ... expected "UTF8"}），H2 的 {@code
 * MODE=MySQL} 只覆盖语法子集、 不覆盖 {@code information_schema} 与 {@code DATABASE()} 的方言。也就是说**用 H2 验这段 DDL
 * 是自欺欺人**： 测的不是 MySQL 的行为，而是「我以为 MySQL 会怎样」。真机验证需另立项（testcontainers 或 CI 加 MySQL service；当前 CI 无
 * MySQL service 且 {@code -DskipITs=true}），本测试不假装覆盖它。
 */
class JdbcPhysicalStructureGatewayAdapterIdempotencyTest {

  private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
  private final MetaEntityRepository entityRepository = mock(MetaEntityRepository.class);
  private final MetaFieldRepository fieldRepository = mock(MetaFieldRepository.class);

  private JdbcPhysicalStructureGatewayAdapter adapter() {
    return new JdbcPhysicalStructureGatewayAdapter(jdbcTemplate, entityRepository, fieldRepository);
  }

  @Test
  @DisplayName("建表语句带 IF NOT EXISTS + 显式 ENGINE/CHARSET")
  void createTableIsIdempotentAndPinned() throws Exception {
    String ddl = buildCreateTable("t_demo", List.of());

    assertTrue(
        ddl.startsWith("CREATE TABLE IF NOT EXISTS `t_demo`"),
        "建表缺 IF NOT EXISTS ⇒ 并发 align 时后到者会撞 Table already exists 并抛裸 SQL 异常：" + ddl);
    assertTrue(
        ddl.contains("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"),
        "建表未固定引擎/字符集 ⇒ 跟随库默认值，跨环境建出的表字符集不一致，" + "后续 information_schema 类型比对会误报「类型漂移」：" + ddl);
  }

  @Test
  @DisplayName("并发下「列已存在」（MySQL 1060/42S11）被判为已达成而非失败")
  void duplicateColumnIsTreatedAsAchieved() {
    Method isDuplicateColumn = privateMethod("isDuplicateColumn", Throwable.class);

    assertTrue(
        invokeBoolean(
            isDuplicateColumn, new DataAccessResourceFailureException("wrap", dupColumn())),
        "MySQL 1060 未被识别 ⇒ 并发 align 的后到者会失败，而它本该视为「另一方已加好」");
    assertTrue(
        invokeBoolean(
            isDuplicateColumn, new DataAccessResourceFailureException("Duplicate column name 'x'")),
        "仅靠错误消息识别的实现，在驱动/包装层数变化时会漏判");
    assertFalse(
        invokeBoolean(
            isDuplicateColumn,
            new DataAccessResourceFailureException(
                "wrap", new SQLException("syntax error", "42000", 1064))),
        "非重复列错误被误判为「已达成」⇒ 真实失败被静默吞掉，结构缺口永远查不出来");
  }

  @Test
  @DisplayName("非重复列的 SQL 异常必须继续向上抛（不得被幂等逻辑吞掉）")
  void otherSqlErrorsStillPropagate() {
    org.mockito.Mockito.doThrow(
            new DataAccessResourceFailureException(
                "wrap", new SQLException("table not exist", "42S02", 1146)))
        .when(jdbcTemplate)
        .execute(org.mockito.ArgumentMatchers.anyString());

    DataAccessResourceFailureException thrown =
        assertThrows(
            DataAccessResourceFailureException.class,
            () -> invokeExecuteIdempotently("ALTER TABLE `t_demo` ADD COLUMN `c1` INT"));

    assertTrue(
        thrown.getCause() instanceof SQLException sql && "table not exist".equals(sql.getMessage()),
        "抛出的不是原始异常（或错误现场丢失）⇒ 中间被替换过，排障时看不到真正的 SQL 原因");
  }

  private void invokeExecuteIdempotently(String ddl) {
    try {
      privateMethod("executeIdempotently", String.class).invoke(adapter(), ddl);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new IllegalStateException(cause);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  // ---------------------------------------------------------------- 反射工具

  private String buildCreateTable(String table, List<MetaField> fields) throws Exception {
    Method m =
        JdbcPhysicalStructureGatewayAdapter.class.getDeclaredMethod(
            "buildCreateTable", String.class, List.class);
    m.setAccessible(true);
    return (String) m.invoke(adapter(), table, fields);
  }

  private static Method privateMethod(String name, Class<?> param) {
    try {
      Method m = JdbcPhysicalStructureGatewayAdapter.class.getDeclaredMethod(name, param);
      m.setAccessible(true);
      return m;
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("被测方法不存在: " + name + "（改名会让本测试静默失效）", e);
    }
  }

  private static boolean invokeBoolean(Method m, Object arg) {
    try {
      return (Boolean) m.invoke(null, arg);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(e);
    }
  }

  /** MySQL「列已存在」：errorCode 1060 / SQLState 42S11。 */
  private static SQLException dupColumn() {
    return new SQLException("Duplicate column name 'c1'", "42S11", 1060);
  }
}
