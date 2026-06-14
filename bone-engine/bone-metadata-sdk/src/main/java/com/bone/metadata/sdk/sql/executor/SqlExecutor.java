package com.bone.metadata.sdk.sql.executor;

import com.bone.core.domain.id.GenerationStrategy;
import com.bone.core.domain.id.IdGenerator;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.domain.query.*;
import com.bone.metadata.sdk.domain.spec.TableMetadataResolver;
import com.bone.metadata.sdk.sql.processor.SqlSecurityGuard;
import com.bone.metadata.sdk.support.config.SqlConfigProperties;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Transactional
public class SqlExecutor {

  private final NamedParameterJdbcOperations jdbc;
  private final IdGenerator idGenerator;

  @Autowired
  public SqlExecutor(NamedParameterJdbcOperations jdbc, SqlConfigProperties properties) {
    this.jdbc = Objects.requireNonNull(jdbc);
    this.idGenerator = new DefaultIdGenerator(jdbc);
    if (jdbc.getJdbcOperations() instanceof JdbcTemplate) {
      ((JdbcTemplate) jdbc.getJdbcOperations()).setFetchSize(100);
    }
  }

  public Object generateId(GenerationStrategy strategy, Object entity) {
    return idGenerator.generateId(strategy, entity);
  }

  /* ====================== 查询 ====================== */
  public <T> List<T> queryList(CompiledQuery query, Class<T> entityClass) {
    logSql(query);
    SqlSecurityGuard.scanForInjectionKeywords(query.getSql());
    return jdbc.query(
        query.getSql(),
        new MapSqlParameterSource(query.getParameters()),
        new SmartRowMapper<>(entityClass));
  }

  public <T> List<T> query(CompiledQuery query, Class<T> resultType) {
    logSql(query);
    SqlSecurityGuard.scanForInjectionKeywords(query.getSql());
    return jdbc.query(
        query.getSql(),
        new MapSqlParameterSource(query.getParameters()),
        new SmartRowMapper<>(resultType));
  }

  public <T> T querySingle(CompiledQuery query, Class<T> resultType) {
    try {
      logSql(query);
      SqlSecurityGuard.scanForInjectionKeywords(query.getSql());
      return jdbc.queryForObject(
          query.getSql(),
          new MapSqlParameterSource(query.getParameters()),
          new SmartRowMapper<>(resultType));
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  public <R> R queryForObject(CompiledQuery query, Class<R> requiredType) {
    try {
      logSql(query);
      SqlSecurityGuard.scanForInjectionKeywords(query.getSql());
      return jdbc.queryForObject(
          query.getSql(), new MapSqlParameterSource(query.getParameters()), requiredType);
    } catch (EmptyResultDataAccessException e) {
      return null;
    }
  }

  public List<Map<String, Object>> queryForMap(CompiledQuery query) {
    logSql(query);
    SqlSecurityGuard.scanForInjectionKeywords(query.getSql());
    return jdbc.queryForList(query.getSql(), query.getParameters());
  }

  /* ====================== 更新 ====================== */
  public int update(CompiledQuery query) {
    if (query instanceof CompositeQuery) {
      return compositeUpdate((CompositeQuery) query);
    }
    logSql(query);
    SqlSecurityGuard.scanForInjectionKeywords(query.getSql());
    return jdbc.update(query.getSql(), new MapSqlParameterSource(query.getParameters()));
  }

  public <R> R insert(CompiledQuery query, Class<?> entityClass) {
    logSql(query);
    SqlSecurityGuard.scanForInjectionKeywords(query.getSql());
    KeyHolder keyHolder = new GeneratedKeyHolder();
    TableMetadata meta = TableMetadataResolver.load(entityClass);

    jdbc.update(
        query.getSql(),
        new MapSqlParameterSource(query.getParameters()),
        keyHolder,
        new String[] {meta.getPrimaryKey().getName()});

    @SuppressWarnings("unchecked")
    R result = (R) keyHolder.getKey();
    return result;
  }

  public int[] batchUpdate(BatchCompiledQuery query) {
    logSql(query);
    SqlSecurityGuard.scanForInjectionKeywords(query.getSql());
    return jdbc.batchUpdate(
        query.getSql(), SqlParameterSourceUtils.createBatch(query.getBatchParameters()));
  }

  private int compositeUpdate(CompositeQuery composite) {
    int total = 0;
    for (CompiledQuery seg : composite.getSegments()) {
      total += update(seg);
    }
    return total;
  }

  public long count(CompiledQuery query) {
    Long cnt = queryForObject(query, Long.class);
    return cnt != null ? cnt : 0L;
  }

  /* ====================== 便捷方法 ====================== */
  public int execute(String sql, Object... params) {
    return update(new CompiledQuery(sql, params));
  }

  public int execute(String sql, Map<String, Object> params) {
    return update(new CompiledQuery(sql, params));
  }

  /* ====================== 测试专用 ====================== */
  public int delete(String sql, Object... params) {
    log.debug("TEST DELETE: {}", sql);
    Map<String, Object> map = new HashMap<>();
    for (int i = 0; i < params.length; i++) {
      map.put("p" + i, params[i]);
    }
    return jdbc.update(sql, map);
  }

  /* ====================== 工具 ====================== */
  private void logSql(CompiledQuery q) {
    if (log.isDebugEnabled()) {
      log.debug("SQL: {}\nParams: {}", q.getSql(), q.getParameters());
    }
  }

  private void logSql(BatchCompiledQuery q) {
    if (log.isDebugEnabled()) {
      log.debug("Batch SQL: {}", q.getSql());
    }
  }
}
