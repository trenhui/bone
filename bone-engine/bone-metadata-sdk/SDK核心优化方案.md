# Bone Metadata SDK 核心优化方案

## 一、现状分析

通过对bone-metadata-sdk模块的深入分析，我们发现两个核心问题：

### 1. 使用门槛高

- **API设计复杂**：用户需要理解Repository接口、Criteria API、SqlBuilder等多个组件之间的协作关系
- **学习曲线陡峭**：开发者需要掌握不同的查询构建方式和参数传递模式
- **代码冗余**：实现相同功能需要编写更多的样板代码

### 2. 功能割裂

- **查询构建分散**：查询逻辑分散在Repository、Criteria、SqlBuilder等多个类中
- **缺乏统一入口**：没有一个简洁的入口点来处理所有的查询和操作
- **API不一致**：不同操作（查询、更新、删除）使用不同的调用方式

## 二、优化目标

1. **降低使用门槛**：提供直观、简洁的API，减少学习成本
2. **统一功能入口**：创建单一入口点，整合所有操作
3. **简化查询构建**：提供流畅的链式API，使查询构建更加直观
4. **保持向下兼容**：确保现有代码不受影响，支持平滑迁移

## 三、核心优化方案

### 3.1 统一入口设计：BoneQuery 类

创建一个静态工厂类作为所有操作的统一入口，采用流式API设计。

```java
package com.bone.metadata.sdk.query;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationReadyEvent;

/**
 * Bone Metadata SDK 的统一入口类
 * 提供静态工厂方法来创建各种查询和操作
 */
public class BoneQuery implements ApplicationListener<ApplicationReadyEvent> {
    
    private static BoneQuery INSTANCE;
    private final MetadataSdkContext context;
    
    /**
     * 私有构造函数，通过Spring注入上下文
     */
    public BoneQuery(MetadataSdkContext context) {
        this.context = context;
        // 注意：Spring启动时会创建此实例，但可能不是在ApplicationContext完全初始化后
    }
    
    /**
     * 应用启动完成后设置INSTANCE
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        INSTANCE = this;
    }
    
    /**
     * 获取BoneQuery实例
     */
    private static BoneQuery getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("BoneQuery尚未初始化，请确保Spring应用已正确启动或已配置BoneQueryAutoConfiguration");
        }
        return INSTANCE;
    }
    
    /**
     * 创建查询操作
     */
    public static <T> Query<T> select(Class<T> entityClass) {
        return getInstance().context.createQuery(entityClass);
    }
    
    /**
     * 创建更新操作
     */
    public static <T> Update<T> update(Class<T> entityClass) {
        return getInstance().context.createUpdate(entityClass);
    }
    
    /**
     * 创建删除操作
     */
    public static <T> Delete<T> delete(Class<T> entityClass) {
        return getInstance().context.createDelete(entityClass);
    }
    
    /**
     * 创建批量操作
     */
    public static <T> Batch<T> batch(Class<T> entityClass) {
        return getInstance().context.createBatch(entityClass);
    }
}
```

### 3.2 统一查询接口：Query

设计一个功能完整的Query接口，支持所有常见的查询操作，并提供流畅的链式API。

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
    public List<T> list() {
        BaseRepository<T> repository = context.getRepository(entityClass);
        return repository.findByCriteria(criteria);
    }
    
    // 其他方法实现...
    // 所有方法都返回this以支持链式调用
}
```

### 3.3 增强Lambda表达式解析

使用ASM字节码分析来改进Lambda表达式解析，解决直接字段引用时返回"field"的问题。

```java
package com.bone.metadata.sdk.support.util;

import com.bone.metadata.sdk.support.function.SFunction;
import org.objectweb.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 增强的Lambda表达式解析工具
 */
public class EnhancedLambdaUtil {

    public static <T, R> String extractFieldName(SFunction<T, R> func) {
        try {
            // 获取SerializedLambda对象
            Method method = func.getClass().getDeclaredMethod("writeReplace");
            method.setAccessible(true);
            SerializedLambda serializedLambda = (SerializedLambda) method.invoke(func);
            
            // 获取实现类和方法名
            String implClass = serializedLambda.getImplClass();
            String implMethodName = serializedLambda.getImplMethodName();
            
            // 处理getter方法
            if (implMethodName.startsWith("get") && implMethodName.length() > 3) {
                return decapitalize(implMethodName.substring(3));
            } else if (implMethodName.startsWith("is") && implMethodName.length() > 2) {
                return decapitalize(implMethodName.substring(2));
            }
            
            // 处理直接字段引用，使用ASM字节码分析
            String className = implClass.replace("/", ".");
            try {
                Class<?> clazz = Class.forName(className);
                try (InputStream is = clazz.getClassLoader().getResourceAsStream(implClass + ".class")) {
                    if (is != null) {
                        ClassReader classReader = new ClassReader(is);
                        FieldExtractor fieldExtractor = new FieldExtractor(implMethodName);
                        classReader.accept(fieldExtractor, ClassReader.SKIP_DEBUG);
                        
                        String extractedFieldName = fieldExtractor.getFieldName();
                        if (extractedFieldName != null) {
                            return extractedFieldName;
                        }
                    }
                }
            } catch (Exception e) {
                // 忽略异常，继续执行
            }
            
            // 回退处理
            return handleDirectFieldReference(serializedLambda);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract field name from lambda expression", e);
        }
    }
    
    private static String handleDirectFieldReference(SerializedLambda serializedLambda) {
        // 使用参数类型哈希作为字段名的一部分，确保唯一性
        String[] capturedArgTypes = serializedLambda.getCapturedArgTypes();
        return "field" + Arrays.hashCode(capturedArgTypes);
    }
    
    public static String decapitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        char[] chars = str.toCharArray();
        if (chars.length > 1 && Character.isUpperCase(chars[1])) {
            return str; // 保留大写开头（如URL、ID等）
        }
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }
    
    // ASM访问器实现...
}
```

### 3.4 Repository桥接适配器

创建适配器类，确保BoneQuery能够正确调用现有的BaseRepository功能。

```java
package com.bone.metadata.sdk.query.adapter;

import com.bone.metadata.sdk.repository.BaseRepository;
import com.bone.metadata.sdk.repository.Repository;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Repository桥接适配器，连接BoneQuery和BaseRepository
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
            throw new IllegalArgumentException("No repository found for entity class: " + entityClass.getName());
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

## 四、Spring Boot集成优化

### 4.1 自动配置类

```java
package com.bone.metadata.sdk.autoconfigure;

import com.bone.metadata.sdk.query.BoneQuery;
import com.bone.metadata.sdk.query.adapter.RepositoryBridgeAdapter;
import com.bone.metadata.sdk.repository.BaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * BoneQuery的Spring Boot自动配置类
 */
@Configuration(proxyBeanMethods = false)
public class BoneQueryAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public RepositoryBridgeAdapter repositoryBridgeAdapter(
            @Autowired(required = false) List<BaseRepository<?>> repositories) {
        repositories = repositories != null ? repositories : List.of();
        return new RepositoryBridgeAdapter(repositories);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public BoneQuery boneQuery(MetadataSdkContext metadataSdkContext) {
        return new BoneQuery(metadataSdkContext);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public MetadataSdkContext metadataSdkContext(RepositoryBridgeAdapter repositoryBridgeAdapter) {
        return new DefaultMetadataSdkContext(repositoryBridgeAdapter);
    }
}
```

### 4.2 元数据SDK上下文

```java
package com.bone.metadata.sdk.query;

import com.bone.metadata.sdk.query.adapter.RepositoryBridgeAdapter;
import com.bone.metadata.sdk.repository.BaseRepository;

/**
 * 元数据SDK上下文，管理查询构建器的创建和Repository的访问
 */
public interface MetadataSdkContext {
    
    <T> Query<T> createQuery(Class<T> entityClass);
    
    <T> Update<T> createUpdate(Class<T> entityClass);
    
    <T> Delete<T> createDelete(Class<T> entityClass);
    
    <T> Batch<T> createBatch(Class<T> entityClass);
    
    <T> BaseRepository<T> getRepository(Class<T> entityClass);
}

/**
 * 默认的MetadataSdkContext实现
 */
public class DefaultMetadataSdkContext implements MetadataSdkContext {
    
    private final RepositoryBridgeAdapter repositoryBridgeAdapter;
    
    public DefaultMetadataSdkContext(RepositoryBridgeAdapter repositoryBridgeAdapter) {
        this.repositoryBridgeAdapter = repositoryBridgeAdapter;
    }
    
    @Override
    public <T> Query<T> createQuery(Class<T> entityClass) {
        return new DefaultQueryImpl<>(entityClass, this);
    }
    
    @Override
    public <T> Update<T> createUpdate(Class<T> entityClass) {
        return new DefaultUpdateImpl<>(entityClass, this);
    }
    
    @Override
    public <T> Delete<T> createDelete(Class<T> entityClass) {
        return new DefaultDeleteImpl<>(entityClass, this);
    }
    
    @Override
    public <T> Batch<T> createBatch(Class<T> entityClass) {
        return new DefaultBatchImpl<>(entityClass, this);
    }
    
    @Override
    public <T> BaseRepository<T> getRepository(Class<T> entityClass) {
        return repositoryBridgeAdapter.getRepository(entityClass);
    }
}
```

## 五、使用示例对比

### 5.1 基本查询

```java
// 优化前
Criteria<User> criteria = Criteria.create(User.class).eq("status", "ACTIVE");
List<User> users = userRepository.findByCriteria(criteria);

// 优化后
List<User> users = BoneQuery.select(User.class).eq(User::getStatus, "ACTIVE").list();
```

### 5.2 复杂条件查询

```java
// 优化前
Criteria<User> criteria = Criteria.create(User.class)
    .eq("status", "ACTIVE")
    .and(Criteria.create(User.class)
        .gt("createTime", startTime)
        .lt("createTime", endTime))
    .or(Criteria.create(User.class)
        .eq("specialUser", true));
criteria.orderBy("createTime", SortDirection.DESC);
List<User> users = userRepository.findByCriteria(criteria);

// 优化后
List<User> users = BoneQuery.select(User.class)
    .eq(User::getStatus, "ACTIVE")
    .and(query -> query
        .gt(User::getCreateTime, startTime)
        .lt(User::getCreateTime, endTime))
    .or(query -> query
        .eq(User::isSpecialUser, true))
    .orderByDesc(User::getCreateTime)
    .list();
```

### 5.3 分页查询

```java
// 优化前
Criteria<User> criteria = Criteria.create(User.class)
    .like("name", "%张%")
    .page(1, 10);
PageResult<User> pageResult = userRepository.pageByCriteria(criteria);

// 优化后
PageResult<User> pageResult = BoneQuery.select(User.class)
    .like(User::getName, "%张%")
    .page(1, 10)
    .page();
```

## 六、兼容性与迁移策略

### 6.1 向下兼容保证

1. **保留所有现有接口**：不修改现有Repository接口和方法
2. **适配器模式**：BoneQuery的实现通过适配器调用现有功能
3. **渐进式迁移**：支持新旧API混用，允许逐步迁移

### 6.2 迁移路径建议

1. **阶段一**：引入新API，保持现有代码不变
2. **阶段二**：新代码使用BoneQuery API
3. **阶段三**：逐步重构现有代码，使用迁移辅助工具
4. **阶段四**：（未来）考虑标记旧API为@Deprecated

## 七、实施计划

1. **准备阶段**：
   - 创建核心接口：Query、Update、Delete、Batch
   - 实现BoneQuery统一入口类

2. **开发阶段**：
   - 实现查询构建器适配器
   - 改进Lambda表达式解析
   - 开发Spring Boot自动配置

3. **测试阶段**：
   - 单元测试覆盖所有新API
   - 集成测试验证与现有功能的兼容性

4. **文档阶段**：
   - 更新API文档
   - 编写迁移指南
   - 提供丰富的示例代码

## 八、总结

本优化方案通过创建统一入口类BoneQuery、设计流畅的链式API、改进Lambda表达式解析，并确保与现有系统的兼容性，有效解决了SDK使用门槛高和功能割裂的问题。优化后的SDK将提供更加直观、简洁的编程体验，降低学习成本，同时保持与现有代码的兼容性，支持平滑迁移。