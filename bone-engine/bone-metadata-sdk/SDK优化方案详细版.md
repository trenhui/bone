# bone-metadata-sdk 深度优化方案

## 一、现状分析

通过对bone-metadata-sdk代码的深入分析，我们发现以下核心问题：

### 1. 使用门槛高，API设计复杂

- **多入口问题**：SDK目前提供了多种查询方式（Repository接口、Criteria查询、DSL查询），但它们之间缺乏统一的入口和清晰的关系
- **组件协作复杂**：开发者需要理解Repository、Criteria、SqlBuilder及其各种实现类等多个组件之间的协作关系
- **冗余方法**：BaseRepository实现了大量类似功能但参数形式不同的方法（如queryByCondition、query、queryPage等）
- **参数过多**：部分通用查询方法参数过多（如queryByCondition有5个参数），使用不够直观
- **文档不完整**：缺乏系统化的API参考和使用示例

### 2. 功能割裂，查询构建分散

- **查询构建分散**：查询构建逻辑分散在多个类中（Criteria、SqlBuilder接口、DSL相关SqlBuilder等）
- **API重叠**：Criteria API和DSL API并行存在，功能重叠但使用方式不同
- **条件处理分离**：主表条件和扩展表条件分离处理（eq vs eqExtra方法），增加了使用复杂度
- **SQL构建逻辑分散**：不同类型的SQL构建逻辑分散在不同的Builder实现类中，维护和扩展困难

### 3. Lambda表达式解析不完善

- **直接返回"field"**：SqlUtil.extractFieldNameFromLambda方法目前直接返回"field"，无法正确解析字段名
- **支持有限**：Lambda解析仅支持getter方法和布尔is方法，对直接字段引用支持不完善

## 二、优化方案

### 1. 统一入口设计

#### 1.1 创建统一入口类 `BoneQuery`

BoneQuery作为SDK的统一入口，需要确保与Spring Boot框架无缝集成，我们使用Spring的事件机制确保在应用启动完成后再进行初始化。

```java
package com.bone.metadata.sdk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.List;

/**
 * Bone SDK 统一入口类
 * 提供流畅的链式API，简化查询、更新、删除等常见操作
 */
@Component
public class BoneQuery implements ApplicationListener<ApplicationReadyEvent> {
    
    private static BoneQuery INSTANCE;
    private final MetadataSdkContext context;
    
    @Autowired
    public BoneQuery(MetadataSdkContext context) {
        this.context = context;
    }
    
    /**
     * 获取BoneQuery实例
     */
    public static BoneQuery getInstance() {
        Assert.notNull(INSTANCE, "BoneQuery has not been initialized yet. Make sure Spring context is properly loaded.");
        return INSTANCE;
    }
    
    /**
     * 开始查询操作
     * @param entityClass 实体类
     * @param <T> 实体类型
     * @return 查询构建器
     */
    public static <T> Query<T> select(Class<T> entityClass) {
        return getInstance().context.createQuery(entityClass);
    }
    
    /**
     * 开始更新操作
     * @param entityClass 实体类
     * @param <T> 实体类型
     * @return 更新构建器
     */
    public static <T> Update<T> update(Class<T> entityClass) {
        return getInstance().context.createUpdate(entityClass);
    }
    
    /**
     * 开始删除操作
     * @param entityClass 实体类
     * @param <T> 实体类型
     * @return 删除构建器
     */
    public static <T> Delete<T> delete(Class<T> entityClass) {
        return getInstance().context.createDelete(entityClass);
    }
    
    /**
     * 批量操作
     * @param entityClass 实体类
     * @param <T> 实体类型
     * @return 批量操作构建器
     */
    public static <T> Batch<T> batch(Class<T> entityClass) {
        return getInstance().context.createBatch(entityClass);
    }
    
    /**
     * 应用启动完成后初始化实例
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        INSTANCE = this;
    }
}
```

### 1.2 创建BoneQuery自动配置类

```java
package com.bone.metadata.sdk.support.config;

import com.bone.metadata.sdk.BoneQuery;
import com.bone.metadata.sdk.query.DefaultQueryImpl;
import com.bone.metadata.sdk.query.DefaultUpdateImpl;
import com.bone.metadata.sdk.query.DefaultDeleteImpl;
import com.bone.metadata.sdk.query.DefaultBatchImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * BoneQuery自动配置类
 * 确保BoneQuery相关组件正确初始化并集成到Spring Boot应用中
 */
@Configuration
@EnableConfigurationProperties(MetadataSdkProperties.class)
public class BoneQueryAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public BoneQuery boneQuery(MetadataSdkContext metadataSdkContext) {
        return new BoneQuery(metadataSdkContext);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public QueryFactory queryFactory(MetadataSdkContext metadataSdkContext) {
        return new DefaultQueryFactory(metadataSdkContext);
    }
    
    /**
     * 查询工厂接口，用于创建不同类型的查询构建器
     */
    public interface QueryFactory {
        <T> Query<T> createQuery(Class<T> entityClass);
        <T> Update<T> createUpdate(Class<T> entityClass);
        <T> Delete<T> createDelete(Class<T> entityClass);
        <T> Batch<T> createBatch(Class<T> entityClass);
    }
    
    /**
     * 查询工厂默认实现
     */
    public static class DefaultQueryFactory implements QueryFactory {
        
        private final MetadataSdkContext context;
        
        public DefaultQueryFactory(MetadataSdkContext context) {
            this.context = context;
        }
        
        @Override
        public <T> Query<T> createQuery(Class<T> entityClass) {
            return new DefaultQueryImpl<>(entityClass, context);
        }
        
        @Override
        public <T> Update<T> createUpdate(Class<T> entityClass) {
            return new DefaultUpdateImpl<>(entityClass, context);
        }
        
        @Override
        public <T> Delete<T> createDelete(Class<T> entityClass) {
            return new DefaultDeleteImpl<>(entityClass, context);
        }
        
        @Override
        public <T> Batch<T> createBatch(Class<T> entityClass) {
            return new DefaultBatchImpl<>(entityClass, context);
        }
    }
}
```

### 1.3 更新MetadataSdkContext接口

为了支持BoneQuery的功能，我们需要更新MetadataSdkContext接口，添加查询构建器创建方法：

```java
package com.bone.metadata.sdk.support.config;

import com.bone.metadata.sdk.domain.enums.DatabaseType;
import com.bone.metadata.sdk.domain.enums.DeploymentMode;
import com.bone.metadata.sdk.query.Batch;
import com.bone.metadata.sdk.query.Delete;
import com.bone.metadata.sdk.query.Query;
import com.bone.metadata.sdk.query.Update;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Metadata SDK上下文接口
 * 扩展以支持BoneQuery API
 */
public class MetadataSdkContext {
    // 现有属性...
    private final QueryFactory queryFactory;
    
    // 构造函数...
    public MetadataSdkContext(DataSourceProperties dataSourceProperties, QueryFactory queryFactory) {
        // 初始化现有属性...
        this.queryFactory = queryFactory;
    }
    
    // 现有方法...
    
    /**
     * 创建查询构建器
     */
    public <T> Query<T> createQuery(Class<T> entityClass) {
        return queryFactory.createQuery(entityClass);
    }
    
    /**
     * 创建更新构建器
     */
    public <T> Update<T> createUpdate(Class<T> entityClass) {
        return queryFactory.createUpdate(entityClass);
    }
    
    /**
     * 创建删除构建器
     */
    public <T> Delete<T> createDelete(Class<T> entityClass) {
        return queryFactory.createDelete(entityClass);
    }
    
    /**
     * 创建批量操作构建器
     */
    public <T> Batch<T> createBatch(Class<T> entityClass) {
        return queryFactory.createBatch(entityClass);
    }
}
```

### 1.4 配置自动装配

更新自动装配配置，确保BoneQueryAutoConfiguration能够被Spring Boot自动检测到：

在`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`文件中添加：

```
com.bone.metadata.sdk.support.config.BoneQueryAutoConfiguration
```
```

#### 1.2 创建统一的查询接口 `Query`

为了提供流畅的链式API，我们设计了Query接口，它包含了所有常见的查询操作。同时，我们还需要提供具体的实现类，确保它能正确调用底层的BaseRepository功能。

```java
package com.bone.metadata.sdk.query;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.support.function.SFunction;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 统一查询接口，提供流畅的链式API
 */
public interface Query<T> {
    
    // 条件查询方法 - Lambda支持
    <R> Query<T> eq(SFunction<T, R> field, Object value);
    <R> Query<T> ne(SFunction<T, R> field, Object value);
    <R> Query<T> gt(SFunction<T, R> field, Object value);
    <R> Query<T> gte(SFunction<T, R> field, Object value);
    <R> Query<T> lt(SFunction<T, R> field, Object value);
    <R> Query<T> lte(SFunction<T, R> field, Object value);
    <R> Query<T> like(SFunction<T, R> field, String value);
    <R> Query<T> in(SFunction<T, R> field, Collection<?> values);
    <R> Query<T> isNull(SFunction<T, R> field);
    <R> Query<T> isNotNull(SFunction<T, R> field);
    
    // 条件查询方法 - 字符串支持
    Query<T> eq(String field, Object value);
    Query<T> ne(String field, Object value);
    Query<T> gt(String field, Object value);
    Query<T> gte(String field, Object value);
    Query<T> lt(String field, Object value);
    Query<T> lte(String field, Object value);
    Query<T> like(String field, String value);
    Query<T> in(String field, Collection<?> values);
    Query<T> isNull(String field);
    Query<T> isNotNull(String field);
    
    // 扩展字段条件（内部自动处理扩展表）
    <R> Query<T> eqExt(SFunction<T, R> field, Object value);
    Query<T> eqExt(String field, Object value);
    // 其他扩展字段方法...
    
    // 组合条件
    Query<T> and(Consumer<Query<T>> consumer);
    Query<T> or(Consumer<Query<T>> consumer);
    
    // 排序
    <R> Query<T> orderByAsc(SFunction<T, R> field);
    <R> Query<T> orderByDesc(SFunction<T, R> field);
    Query<T> orderByAsc(String field);
    Query<T> orderByDesc(String field);
    
    // 分页
    Query<T> page(int pageNum, int pageSize);
    
    // 结果查询
    List<T> list();
    T one();
    PageResult<T> page();
    long count();
    
    // 字段投影（只查询指定字段）
    Query<T> select(String... fields);
    <R> Query<T> select(SFunction<T, R>... fields);
    
    // 关联查询
    Query<T> join(String table, String onCondition);
    Query<T> leftJoin(String table, String onCondition);
    <R, U> Query<T> leftJoin(Class<U> joinClass, SFunction<T, R> leftField, SFunction<U, R> rightField);
    
    // 聚合查询
    Map<String, Object> aggregate(String... aggregations);
    List<Map<String, Object>> aggregateGroupBy(String[] aggregations, String... groupByFields);
}

/**
 * Query接口的默认实现类
 */
public class DefaultQueryImpl<T> implements Query<T> {
    
    private final Class<T> entityClass;
    private final MetadataSdkContext context;
    private final Criteria<T> criteria;
    
    public DefaultQueryImpl(Class<T> entityClass, MetadataSdkContext context) {
        this.entityClass = entityClass;
        this.context = context;
        this.criteria = Criteria.create(entityClass);
    }
    
    @Override
    public <R> Query<T> eq(SFunction<T, R> field, Object value) {
        criteria.eq(field, value);
        return this;
    }
    
    @Override
    public <R> Query<T> eqExt(SFunction<T, R> field, Object value) {
        criteria.eqExtra(field, value);
        return this;
    }
    
    @Override
    public Query<T> and(Consumer<Query<T>> consumer) {
        // 实现条件组
        DefaultQueryImpl<T> subQuery = new DefaultQueryImpl<>(entityClass, context);
        consumer.accept(subQuery);
        // 将子查询的条件添加到当前条件组
        // ...
        return this;
    }
    
    @Override
    public List<T> list() {
        // 获取对应的Repository并执行查询
        BaseRepository<T> repository = context.getRepository(entityClass);
        return repository.findByCriteria(criteria);
    }
    
    @Override
    public PageResult<T> page() {
        BaseRepository<T> repository = context.getRepository(entityClass);
        return repository.pageByCriteria(criteria);
    }
    
    // 其他方法实现...
    // 所有方法都返回this以支持链式调用
    // 底层委托给Criteria对象或直接调用BaseRepository方法
}
```

#### 1.3 创建统一的更新接口 `Update`

```java
package com.bone.metadata.sdk.query;

import com.bone.metadata.sdk.support.function.SFunction;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 统一更新接口
 */
public interface Update<T> {
    
    // 设置更新字段
    <R> Update<T> set(SFunction<T, R> field, Object value);
    Update<T> set(String field, Object value);
    Update<T> set(Map<String, Object> fieldMap);
    
    // 条件设置（与Query接口共享条件构建逻辑）
    <R> Update<T> eq(SFunction<T, R> field, Object value);
    Update<T> eq(String field, Object value);
    // 其他条件方法...
    
    // 组合条件
    Update<T> and(Consumer<Update<T>> consumer);
    Update<T> or(Consumer<Update<T>> consumer);
    
    // 执行更新
    int execute();
}
```

#### 1.4 创建统一的删除接口 `Delete`

```java
package com.bone.metadata.sdk.query;

import com.bone.metadata.sdk.support.function.SFunction;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * 统一删除接口
 */
public interface Delete<T> {
    
    // 根据ID删除
    Delete<T> byId(Object id);
    Delete<T> byIds(Collection<?> ids);
    
    // 条件删除（与Query接口共享条件构建逻辑）
    <R> Delete<T> eq(SFunction<T, R> field, Object value);
    Delete<T> eq(String field, Object value);
    // 其他条件方法...
    
    // 组合条件
    Delete<T> and(Consumer<Delete<T>> consumer);
    Delete<T> or(Consumer<Delete<T>> consumer);
    
    // 执行删除
    int execute();
}
```

#### 1.5 创建统一的批量操作接口 `Batch`

```java
package com.bone.metadata.sdk.query;

import java.util.List;

/**
 * 批量操作接口
 */
public interface Batch<T> {
    
    // 设置批处理大小
    Batch<T> batchSize(int batchSize);
    
    // 批量插入
    int insert(List<T> entities);
    
    // 批量更新
    int update(List<T> entities);
    
    // 批量保存（根据ID存在性决定插入或更新）
    int save(List<T> entities);
    
    // 批量删除
    int delete(List<T> entities);
}
```

### 2. 功能整合与API简化

#### 2.1 重构 `BaseRepository`，整合冗余方法

将BaseRepository中的queryByCondition、query、queryPage等方法整合，减少冗余API，并添加对BoneQuery的支持。同时，我们需要创建一个桥接适配器，确保BoneQuery能够正确调用现有的BaseRepository功能。

将BaseRepository中的queryByCondition、query、queryPage等方法整合，减少冗余API：

```java
// 整合后的代码示例（部分）
@Override
public PageResult<T> queryByCondition(List<QueryParam> queryParams, 
                                     List<SortingField> sortingFields, 
                                     Integer pageNo, 
                                     Integer pageSize, 
                                     String bizIdentityCode) {
    Criteria<T> criteria = buildCriteria(queryParams, bizIdentityCode);
    addSortingToCriteria(criteria, sortingFields);
    criteria.page(pageNo, pageSize);
    return pageByCriteria(criteria);
}

@Override
public List<T> query(Query queryParam) {
    Criteria<T> criteria = buildCriteriaFromQuery(queryParam);
    return findByCriteria(criteria);
}

@Override
public PageResult<T> queryPage(PageParam pageParam) {
    Criteria<T> criteria = buildCriteriaFromQuery(pageParam);
    criteria.page(pageParam.getPageNo(), pageParam.getPageSize());
    return pageByCriteria(criteria);
}

// 新增适配方法，将BoneQuery API调用委托给BaseRepository
public <T> Query<T> createQuery(Class<T> entityClass) {
    return new QueryImpl<>(entityClass, this);
}

/**
 * 创建Repository桥接适配器，连接BoneQuery和BaseRepository
 */
public class RepositoryBridgeAdapter {
    
    private final Map<Class<?>, BaseRepository<?>> repositoryMap;
    
    public RepositoryBridgeAdapter(List<BaseRepository<?>> repositories) {
        this.repositoryMap = repositories.stream()
            .collect(Collectors.toMap(
                repo -> extractEntityType(repo.getClass()),
                Function.identity()
            ));
    }
    
    @SuppressWarnings("unchecked")
    public <T> BaseRepository<T> getRepository(Class<T> entityClass) {
        BaseRepository<T> repository = (BaseRepository<T>) repositoryMap.get(entityClass);
        if (repository == null) {
            // 如果没有找到对应的Repository，可以创建一个默认的Repository实例
            // 或者抛出异常
        }
        return repository;
    }
    
    private Class<?> extractEntityType(Class<?> repositoryClass) {
        // 提取Repository实现类的泛型参数类型
        Type[] genericInterfaces = repositoryClass.getGenericInterfaces();
        for (Type genericInterface : genericInterfaces) {
            if (genericInterface instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericInterface;
                if (parameterizedType.getRawType() == Repository.class) {
                    return (Class<?>) parameterizedType.getActualTypeArguments()[0];
                }
            }
        }
        // 查找父类的泛型参数
        Type genericSuperclass = repositoryClass.getGenericSuperclass();
        if (genericSuperclass instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericSuperclass;
            if (parameterizedType.getRawType() == BaseRepository.class) {
                return (Class<?>) parameterizedType.getActualTypeArguments()[0];
            }
        }
        throw new IllegalArgumentException("Cannot extract entity type from " + repositoryClass.getName());
    }
}
```

#### 2.2 重构 `Criteria` 类，简化条件构建

- 合并主表和扩展表条件处理逻辑，通过内部标记区分
- 提供更简洁的条件组合方式
- 优化排序和分页设置

```java
public class Criteria<T> {
    // 整合条件处理，内部通过标记区分主表和扩展表
    private final List<Condition> conditions = new ArrayList<>();
    
    // 新增条件分组支持
    public Criteria<T> and(Consumer<Criteria<T>> consumer) {
        Criteria<T> subCriteria = new Criteria<>();
        consumer.accept(subCriteria);
        if (!subCriteria.getConditions().isEmpty()) {
            this.conditions.add(new GroupCondition(subCriteria.getConditions(), false));
        }
        return this;
    }
    
    // 统一的条件添加方法
    private <R> Criteria<T> addCondition(boolean extension, SFunction<T, R> fn, Operator op, Object... vals) {
        String fieldName = SqlUtil.getFieldName(fn);
        return addCondition(extension, fieldName, op, vals);
    }
}
```

#### 2.3 重构 SQL 构建逻辑，统一构建器

合并多个SQL构建器，提供统一的SQL生成逻辑：

```java
public class UnifiedSqlBuilder {
    // 统一的SQL构建方法
    public CompiledQuery buildQuery(QueryContext context) {
        switch (context.getType()) {
            case SELECT -> {
                return buildSelectQuery(context);
            }
            case UPDATE -> {
                return buildUpdateQuery(context);
            }
            case DELETE -> {
                return buildDeleteQuery(context);
            }
            // 其他类型...
        }
    }
    
    // 统一处理条件构建
    private String buildConditions(List<Condition> conditions) {
        // 统一的条件构建逻辑
    }
}
```

### 3. 增强 Lambda 表达式解析

#### 3.1 改进 `SqlUtil.extractFieldNameFromLambda` 方法

使用ASM或Javassist等字节码分析库改进Lambda表达式解析：

```java
/**
 * 改进的Lambda字段提取方法
 */
private static String extractFieldNameFromLambda(SerializedLambda lambda) {
    try {
        String implClass = lambda.getImplClass().replace("/", ".");
        String implMethodName = lambda.getImplMethodName();
        
        // 对于lambda$开头的方法（直接字段引用），尝试通过字节码分析提取
        if (implMethodName.startsWith("lambda$")) {
            // 方案1: 使用ASM库分析字节码
            // 方案2: 使用反射 + 方法名解析（更简单的方案）
            return analyzeLambdaByteCode(implClass, implMethodName, lambda);
        }
        return null;
    } catch (Exception e) {
        return null;
    }
}

/**
 * 使用ASM分析Lambda字节码提取字段名
 */
private static String analyzeLambdaByteCode(String implClass, String implMethodName, SerializedLambda lambda) {
    try {
        // 从lambda中获取实现方法的签名
        String signature = lambda.getImplMethodSignature();
        // 解析方法签名，提取字段信息
        // 这里使用ASM进行实际的字节码分析
        // ...
        
        // 简化实现示例：从捕获的变量和方法操作中提取字段名
        return extractFieldFromSignature(signature);
    } catch (Exception e) {
        return null;
    }
}

/**
 * 从方法签名中提取字段信息
 */
private static String extractFieldFromSignature(String signature) {
    // 解析方法签名，提取可能的字段信息
    // 这是一个简化实现，实际需要更复杂的解析逻辑
    // ...
    return parseFieldFromSignature(signature);
}
```

#### 3.2 添加ASM依赖支持

在pom.xml中添加ASM依赖：

```xml
<dependency>
    <groupId>org.ow2.asm</groupId>
    <artifactId>asm</artifactId>
    <version>9.4</version>
</dependency>
<dependency>
    <groupId>org.ow2.asm</groupId>
    <artifactId>asm-commons</artifactId>
    <version>9.4</version>
</dependency>
```

### 4. 实现示例

#### 4.1 统一查询入口使用示例

```java
// 简单查询
List<User> users = BoneQuery.select(User.class)
    .eq(User::getName, "张三")
    .like(User::getEmail, "%@example.com")
    .orderByDesc(User::getCreateTime)
    .list();

// 分页查询
PageResult<User> pageResult = BoneQuery.select(User.class)
    .gt(User::getAge, 18)
    .page(1, 10)
    .page();

// 条件组合
List<User> activeUsers = BoneQuery.select(User.class)
    .and(q -> q.eq(User::getStatus, "ACTIVE")
              .or(q2 -> q2.gt(User::getScore, 100)
                          .eq(User::getLevel, 5)))
    .list();

// 批量操作
int count = BoneQuery.batch(User.class)
    .batchSize(500)
    .insert(userList);

// 更新操作
int updated = BoneQuery.update(User.class)
    .set(User::getStatus, "INACTIVE")
    .lt(User::getLastLoginTime, oneMonthAgo)
    .execute();

// 删除操作
int deleted = BoneQuery.delete(User.class)
    .eq(User::getIsDeleted, true)
    .lt(User::getDeleteTime, oneYearAgo)
    .execute();
```

#### 4.2 扩展表字段查询示例

```java
// 查询包含扩展字段的用户信息
List<User> users = BoneQuery.select(User.class)
    .eq(User::getId, 1L)
    .eqExt(User::getExtraInfo, "VIP")  // 自动处理扩展表关联
    .list();
```

## 三、实施路线

### 1. 第一阶段：统一入口设计

1. 创建 `BoneQuery` 统一入口类和相关接口
2. 设计并实现 `Query`、`Update`、`Delete`、`Batch` 接口
3. 创建 `BoneQueryAutoConfiguration` 自动配置类
4. 实现与现有 `BaseRepository` 的桥接适配器

### 2. 第二阶段：功能整合

1. 重构 `Criteria` 类，整合条件处理逻辑
2. 合并 SQL 构建器，统一 SQL 生成逻辑
3. 简化 `BaseRepository` 中的冗余方法
4. 更新 `MetadataSdkContext` 以支持查询构建器创建

### 3. 第三阶段：增强 Lambda 支持

1. 改进 `SqlUtil.extractFieldNameFromLambda` 方法
2. 添加 ASM 依赖支持
3. 实现完整的 Lambda 表达式解析
4. 增加单元测试确保 Lambda 解析的正确性

### 4. 第四阶段：完善文档和测试

1. 创建详细的 API 文档
2. 提供丰富的使用示例
3. 编写全面的单元测试
4. 更新 Spring Boot 集成指南

## 四、兼容性与迁移方案

为确保平稳迁移，我们将：

1. **保留原有 API**：不删除任何现有接口和方法，确保向后兼容
2. **添加废弃标记**：为旧 API 添加 `@Deprecated` 注解，引导用户使用新 API
3. **提供迁移指南**：详细说明如何从旧 API 迁移到新 API
4. **桥接模式**：新 API 的实现内部委托给现有实现，确保行为一致性

## 五、预期收益

1. **降低使用门槛**：通过统一的入口和流畅的 API，大大降低学习和使用成本
2. **减少代码量**：链式调用减少样板代码，提高开发效率
3. **提高可维护性**：统一的构建逻辑和清晰的接口定义使代码更易维护
4. **增强功能**：更好的 Lambda 支持和更强大的查询能力
5. **与主流框架接轨**：API 设计参考 MyBatis-Plus、JOOQ 等主流框架，降低学习曲线

## 六、优化前后对比

### 优化前（复杂的API设计）

```java
// 使用Criteria构建查询
Criteria<User> criteria = Criteria.<User>create()
    .eq(User::getName, "张三")
    .like(User::getEmail, "%@example.com")
    .addSort(User::getCreateTime, SortDirection.DESC)
    .page(1, 10);

// 通过Repository执行查询
List<User> users = userRepository.findByCriteria(criteria);

// 条件更新需要分开设置entity和criteria
User user = new User();
user.setStatus("INACTIVE");
Criteria<User> updateCriteria = Criteria.<User>create()
    .lt(User::getLastLoginTime, oneMonthAgo);
userRepository.updateByCriteria(user, updateCriteria);

// 复杂条件组合需要手动创建多个条件对象
List<Condition> conditions = new ArrayList<>();
Condition cond1 = Condition.create(User.class, User::getAge, Operator.GT, 18);
Condition cond2 = Condition.create(User.class, User::getLevel, Operator.EQ, 5);
conditions.add(cond1);
conditions.add(cond2);
Criteria<User> complexCriteria = Criteria.<User>create().addConditions(conditions);
```

### 优化后（统一流畅的API）

```java
// 统一入口，流畅的链式调用
List<User> users = BoneQuery.select(User.class)
    .eq(User::getName, "张三")
    .like(User::getEmail, "%@example.com")
    .orderByDesc(User::getCreateTime)
    .page(1, 10)
    .list();

// 直观的更新操作
int updated = BoneQuery.update(User.class)
    .set(User::getStatus, "INACTIVE")
    .lt(User::getLastLoginTime, oneMonthAgo)
    .execute();

// 简化的复杂条件组合
List<User> complexUsers = BoneQuery.select(User.class)
    .and(q -> q.gt(User::getAge, 18)
               .eq(User::getLevel, 5))
    .or(q -> q.eq(User::getType, "VIP")
              .and(q2 -> q2.gt(User::getScore, 1000)))
    .orderByDesc(User::getCreateTime)
    .list();

// 扩展字段查询（自动处理扩展表）
User user = BoneQuery.select(User.class)
    .eq(User::getId, 1L)
    .eqExt(User::getExtraInfo, "SPECIAL_USER")
    .one();

// 聚合查询
Map<String, Object> stats = BoneQuery.select(User.class)
    .aggregate("COUNT(*)", "AVG(age)", "MAX(score)");

// 批量操作
List<User> newUsers = // 用户列表
int insertedCount = BoneQuery.batch(User.class)
    .batchSize(500)
    .insert(newUsers);

// 链式分页查询
PageResult<User> pageResult = BoneQuery.select(User.class)
    .eq(User::getDepartmentId, 100)
    .gt(User::getCreateTime, startDate)
    .orderByDesc(User::getCreateTime)
    .page(1, 20)
    .page();

通过以上优化，我们将使bone-metadata-sdk更加易用、功能更加强大，同时保持与现有代码的兼容性。