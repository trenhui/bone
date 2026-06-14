package com.bone.metadata.sdk;

import com.bone.core.domain.entity.Entity;
import com.bone.core.model.*;
import com.bone.metadata.sdk.domain.exception.MultipleResultsException;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.dsl.DefaultFluentQuery;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.metadata.sdk.query.dsl.condition.Condition;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.metadata.sdk.support.function.SFunction;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 通用存储库接口，用于基本的 CRUD 操作。
 *
 * @param <T> 此存储库管理的实体类型
 */
public interface Repository<T extends Entity<ID>, ID> {

  /**
   * 根据 ID 查询实体。
   *
   * @param id 要查询的实体的 ID
   * @return 具有给定 ID 的实体，如果未找到则返回 null
   */
  T findById(ID id);

  /**
   * 根据 ID 查询实体，包含软删除的数据
   *
   * @param id id
   * @return T
   */
  T findByIdIncludingDeleted(ID id);

  /**
   * 根据 ID列表 批量查询实体。
   *
   * @param idList 要查询的实体的 ID
   * @return 具有给定 ID 的实体列表，如果未找到则返回 null
   */
  List<T> findByIds(List<ID> idList);

  /**
   * 根据 idList 查询实体列表，包含软删除的数据
   *
   * @param idList id列表
   * @return 具有给定 ID 的实体列表，如果未找到则返回 null
   */
  List<T> findByIdsIncludingDeleted(List<ID> idList);

  /**
   * 插入新实体。
   *
   * @param entity 要插入的实体
   */
  ID insert(T entity);

  /**
   * 批量插入实体。
   *
   * @param entities 要插入的实体列表
   */
  void batchInsert(List<T> entities);

  /**
   * 更新现有实体。 只更新 value!=null 的字段
   *
   * @param entity 要更新的实体
   */
  boolean update(T entity);

  /**
   * 条件更新
   *
   * @param entity 要更新的实体
   * @param criteria 更新条件
   */
  int updateByCriteria(T entity, Criteria<T> criteria);

  /**
   * 保存实体 当不存在是新增，保存时更新
   *
   * @param entity 保存实体
   */
  ID save(T entity);

  /**
   * 保存实体列表 根据每条记录是否含有id来判断是更新或是插入
   *
   * @param entityList 保存实体
   */
  void batchSave(List<T> entityList);

  /**
   * 根据 ID 删除实体。
   *
   * @param id 要删除的实体的 ID
   */
  boolean deleteById(ID id);

  /**
   * 根据 ID 批量删除实体。
   *
   * @param ids 要删除的实体 ID 列表
   */
  void deleteByIds(List<ID> ids);

  /**
   * 根据条件删除实体。 支持软删除和硬删除，由实体的 @Table 注解决定。
   *
   * @param criteria 删除条件
   * @return 受影响的行数
   */
  int deleteByCriteria(Criteria<T> criteria);

  /**
   * 根据条件查询
   *
   * @param criteria 条件
   * @return 查询对象
   */
  List<T> findByCriteria(Criteria<T> criteria);

  /**
   * 根据条件查询一个对象
   *
   * @param criteria 条件
   * @return 查询对象
   */
  T findOneByCriteria(Criteria<T> criteria) throws MultipleResultsException;

  /**
   * 分页查询
   *
   * @param criteria 条件
   * @return 查询对象
   */
  PageResult<T> pageByCriteria(Criteria<T> criteria);

  /**
   * 查总条数
   *
   * @param criteria 条件
   * @return 总条数
   */
  Long countByCriteria(Criteria<T> criteria);

  /**
   * 全面通用查询 包含查询条件、排序条件、分页
   *
   * @param queryParams 查询条件
   * @param sortingFields 排序条件
   * @param pageNo 分页页码
   * @param pageSize 分页大小
   * @param bizIdentityCode 业务主体码，用于获取表字段
   * @return 查询结果
   */
  PageResult<T> queryByCondition(
      List<QueryParam> queryParams,
      List<SortingField> sortingFields,
      Integer pageNo,
      Integer pageSize,
      String bizIdentityCode);

  /**
   * 通用查询 包含查询条件、排序条件
   *
   * @param queryParam 查询条件
   * @return 查询结果
   */
  List<T> query(Query queryParam);

  /**
   * 通用分页查询 包含查询条件、排序条件、分页
   *
   * @param pageParam 分页查询条件
   * @return 查询结果
   */
  PageResult<T> queryPage(PageParam pageParam);

  /**
   * 执行聚合查询
   *
   * @param aggregations 聚合表达式列表，比如 ["COUNT(*)", "SUM(amount)"]
   * @param criteria 查询条件
   * @param groupBy GROUP BY字段列表
   * @return 聚合结果列表，每个结果是一个Map，键为聚合表达式或字段名，值为对应的值
   */
  List<Map<String, Object>> aggregate(
      List<String> aggregations, Criteria<T> criteria, List<String> groupBy);

  /**
   * 执行聚合查询（包含HAVING子句）
   *
   * @param aggregations 聚合表达式列表
   * @param criteria 查询条件
   * @param groupBy GROUP BY字段列表
   * @param having HAVING条件列表
   * @return 聚合结果列表
   */
  List<Map<String, Object>> aggregate(
      List<String> aggregations, Criteria<T> criteria, List<String> groupBy, List<String> having);

  /**
   * 执行聚合查询（无GROUP BY）
   *
   * @param aggregations 聚合表达式列表，比如 ["COUNT(*)", "SUM(amount)"]
   * @param criteria 查询条件
   * @return 聚合结果Map，键为聚合表达式，值为对应的值
   */
  Map<String, Object> aggregate(List<String> aggregations, Criteria<T> criteria);

  /**
   * 执行带有分页的聚合查询
   *
   * @param aggregations 聚合表达式列表
   * @param criteria 查询条件
   * @param groupBy GROUP BY字段列表
   * @param having having字段列表
   * @param pageNumber 页码
   * @param pageSize 每页大小
   * @return 分页的聚合结果
   */
  PageResult<Map<String, Object>> aggregateWithPagination(
      List<String> aggregations,
      Criteria<T> criteria,
      List<String> groupBy,
      List<String> having,
      int pageNumber,
      int pageSize);

  /** 获取SQL执行器 - 为DSL查询提供底层支持 实现类应提供此方法的具体实现 */
  SqlExecutor getSqlExecutor();

  /** 获取实体类类型 - 为DSL查询提供类型信息 */
  default Class<T> getEntityClass() {
    @SuppressWarnings("unchecked")
    Class<T> entityClass = (Class<T>) getClass().getGenericInterfaces()[0];
    return entityClass;
  }

  /**
   * 创建DSL查询构建器
   *
   * @return DSL查询构建器实例
   */
  default FluentQuery<T> query() {
    SqlExecutor sqlExecutor = getSqlExecutor();
    Objects.requireNonNull(sqlExecutor, "SqlExecutor cannot be null");

    try {
      @SuppressWarnings("unchecked")
      Class<T> entityClass =
          (Class<T>)
              ((java.lang.reflect.ParameterizedType) getClass().getGenericInterfaces()[0])
                  .getActualTypeArguments()[0];
      return new DefaultFluentQuery<>(entityClass, sqlExecutor);
    } catch (Exception e) {
      // 降级方案：使用QueryBuilder静态方法
      return QueryBuilder.from(getEntityClass());
    }
  }

  /**
   * 直接开始条件查询
   *
   * @param fieldGetter 字段获取器
   * @param <F> 字段类型
   * @return 条件构建器
   */
  default <F> Condition<T, F> where(SFunction<T, F> fieldGetter) {
    return query().where(fieldGetter);
  }

  /**
   * 使用Lambda表达式构建条件查询
   *
   * @param conditionBuilder 条件构建器
   * @return DSL查询构建器
   */
  default FluentQuery<T> where(Consumer<FluentQuery.WhereBuilder<T>> conditionBuilder) {
    return query().where(conditionBuilder);
  }
}
