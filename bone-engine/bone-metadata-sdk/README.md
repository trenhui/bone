# Bone Metadata SDK 使用说明文档

## 1. 概述

Bone Metadata SDK 是一个功能强大的元数据管理和数据访问组件，提供了统一的元数据服务、灵活的查询构建API和强大的多数据源支持。该SDK基于Spring框架构建，设计遵循业界最佳实践，支持丰富的数据访问模式和可扩展的插件机制。

### 1.1 主要特性

- **统一的元数据服务**：提供标准化的元数据访问和管理能力
- **灵活的查询构建API**：支持链式调用、条件查询、排序、分页和连接查询
- **强大的多数据源支持**：支持动态数据源切换、主从分离、读写分离
- **注解式数据源切换**：通过注解实现声明式的数据源路由
- **可扩展的插件机制**：支持自定义插件和扩展点
- **Spring Boot自动配置**：与Spring Boot无缝集成

## 2. 快速开始

### 2.1 添加依赖

在项目的`pom.xml`中添加Bone Metadata SDK的依赖：

```xml
<dependency>
    <groupId>com.bone</groupId>
    <artifactId>bone-metadata-sdk</artifactId>
    <version>${bone-metadata-sdk.version}</version>
</dependency>
```

### 2.2 配置数据源

在`application.yml`中配置数据源信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/master_db?useSSL=false
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
    dynamic:
      primary: master
      strict: false
      datasource:
        master:
          url: jdbc:mysql://localhost:3306/master_db?useSSL=false
          username: root
          password: root
          driver-class-name: com.mysql.cj.jdbc.Driver
        slave1:
          url: jdbc:mysql://localhost:3306/slave_db1?useSSL=false
          username: root
          password: root
          driver-class-name: com.mysql.cj.jdbc.Driver
        slave2:
          url: jdbc:mysql://localhost:3306/slave_db2?useSSL=false
          username: root
          password: root
          driver-class-name: com.mysql.cj.jdbc.Driver

bone:
  metadata:
    datasource:
      enabled: true
```

### 2.3 启用SDK

在Spring Boot应用的主类上添加`@EnableBoneMetadata`注解：

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## 3. 核心组件

### 3.1 Repository接口

`Repository`接口是SDK的核心组件之一，定义了基本的CRUD操作：

```java
public interface Repository<T extends Entity<ID>, ID> {
    T findById(ID id);
    List<T> findByIds(List<ID> idList);
    ID insert(T entity);
    void batchInsert(List<T> entities);
    boolean update(T entity);
    int updateByCriteria(T entity, Criteria<T> criteria);
    ID save(T entity);
    void batchSave(List<T> entityList);
    // 更多方法...
}
```

### 3.2 BaseRepository实现

`BaseRepository`是`Repository`接口的默认实现，提供了完整的CRUD操作实现：

```java
public abstract class BaseRepository<T extends Entity<ID>, ID> implements Repository<T, ID> {
    // 实现Repository接口的所有方法
}
```

### 3.3 QueryBuilder API

`QueryBuilder`提供了流畅的查询构建API，支持链式调用、条件查询、排序、分页和连接查询：

```java
public interface IQueryBuilder<T> {
    // 条件方法
    <V> ConditionBuilder<T, V> where(Function<T, V> fieldFunction);
    <V> ConditionBuilder<T, V> where(String fieldName);
    <V> ConditionBuilder<T, V> and(Function<T, V> fieldFunction);
    <V> ConditionBuilder<T, V> and(String fieldName);
    <V> ConditionBuilder<T, V> or(Function<T, V> fieldFunction);
    <V> ConditionBuilder<T, V> or(String fieldName);
    
    // 排序方法
    <V> IQueryBuilder<T> orderBy(Function<T, V> fieldFunction);
    <V> IQueryBuilder<T> orderBy(Function<T, V> fieldFunction, String direction);
    IQueryBuilder<T> orderBy(String fieldName);
    IQueryBuilder<T> orderBy(String fieldName, String direction);
    
    // 分页方法
    IQueryBuilder<T> limit(long limit);
    IQueryBuilder<T> offset(long offset);
    
    // 执行方法
    List<T> list();
    T single();
    long count();
}
```

### 3.4 多数据源支持

SDK提供了完整的多数据源支持，包括动态数据源切换、注解式数据源路由等功能：

#### 3.4.1 DynamicDataSource

`DynamicDataSource`扩展了Spring的`AbstractRoutingDataSource`，提供线程安全的数据源管理和路由功能：

```java
public class DynamicDataSource extends AbstractRoutingDataSource {
    // 数据源管理和路由相关功能
}
```

#### 3.4.2 DataSourceManager

`DataSourceManager`接口定义了数据源注册、获取、切换和健康检查的核心功能：

```java
public interface DataSourceManager {
    void registerDataSource(String name, DataSource dataSource);
    boolean unregisterDataSource(String name);
    DataSource getDataSource(String name);
    DataSource getCurrentDataSource();
    boolean switchDataSource(String name);
    void resetDataSource();
    Set<String> getAllDataSourceNames();
    boolean isDataSourceHealthy(String name);
    String getCurrentDataSourceName();
    <T> T executeWithDataSource(String dataSourceName, Supplier<T> action);
}
```

#### 3.4.3 DataSourceContextHolder

`DataSourceContextHolder`是线程安全的数据源上下文管理器，用于在不同操作之间管理数据源标识符：

```java
public final class DataSourceContextHolder {
    public static String setDataSource(String dataSource);
    public static String clearDataSource();
    public static String getCurrentDataSource();
    public static boolean hasActiveDataSource();
    // 更多方法...
}
```

## 4. 使用指南

### 4.1 创建Repository

创建自定义Repository需要继承`BaseRepository`类并实现相应的接口：

```java
import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserRepositoryImpl extends BaseRepository<User, Long> implements UserRepository {
    
    @Autowired
    public UserRepositoryImpl(SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {
        super(sqlBuilder, sqlExecutor, User.class, extensionCoordinator);
    }
    
    // 自定义方法实现
}
```

### 4.2 使用Repository进行CRUD操作

#### 4.2.1 查询操作

```java
// 根据ID查询
User user = userRepository.findById(1L);

// 批量查询
List<User> users = userRepository.findByIds(Arrays.asList(1L, 2L, 3L));

// 条件查询
Criteria<User> criteria = Criteria.<User>create()
    .eq("status", 1)
    .gt("age", 18);
List<User> activeUsers = userRepository.findByCriteria(criteria);
```

#### 4.2.2 插入操作

```java
// 单条插入
User newUser = new User();
newUser.setName("张三");
newUser.setAge(25);
Long userId = userRepository.insert(newUser);

// 批量插入
List<User> userList = new ArrayList<>();
userList.add(newUser1);
userList.add(newUser2);
userRepository.batchInsert(userList);
```

#### 4.2.3 更新操作

```java
// 根据ID更新
User user = userRepository.findById(1L);
user.setName("李四");
userRepository.update(user);

// 条件更新
Criteria<User> criteria = Criteria.<User>create()
    .eq("departmentId", 1001);
User updateUser = new User();
updateUser.setStatus(2);
int updatedCount = userRepository.updateByCriteria(updateUser, criteria);
```

#### 4.2.4 删除操作

```java
// 根据ID删除
userRepository.deleteById(1L);

// 批量删除
userRepository.deleteByIds(Arrays.asList(1L, 2L, 3L));

// 条件删除
Criteria<User> criteria = Criteria.<User>create()
    .lt("lastLoginTime", new Date(System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000));
userRepository.deleteByCriteria(criteria);
```

### 4.3 使用QueryBuilder

`QueryBuilder`提供了流畅的查询构建API，支持链式调用：

```java
// 基本查询
List<User> users = QueryBuilder.from(User.class)
    .where(User::getStatus).eq(1)
    .and(User::getAge).gt(18)
    .orderBy(User::getCreateTime, "desc")
    .limit(10)
    .offset(0)
    .list();

// 单条查询
User user = QueryBuilder.from(User.class)
    .where("id").eq(1L)
    .single();

// 计数查询
long count = QueryBuilder.from(User.class)
    .where(User::getStatus).eq(1)
    .count();

// 连接查询
List<User> usersWithRole = QueryBuilder.from(User.class)
    .join(Role.class)
    .on(User::getRoleId, Role::getId)
    .where("Role.name").eq("ADMIN")
    .list();
```

### 4.4 使用多数据源

#### 4.4.1 注解式数据源切换

SDK提供了`@DataSourceSwitch`注解，用于声明式地切换数据源。该注解可以应用于方法或类级别，支持在事务中强制切换数据源的功能。

```java
import com.bone.metadata.sdk.support.dataSource.DataSourceSwitch;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    // 使用主数据源（默认）
    public User saveUser(User user) {
        userRepository.save(user);
        return user;
    }
    
    // 使用从数据源
    @DataSourceSwitch("slave1")
    public User getUserById(Long id) {
        return userRepository.findById(id);
    }
    
    // 在事务中强制使用主数据源
    @DataSourceSwitch(value = "master", force = true)
    public User updateUserInTransaction(User user) {
        userRepository.update(user);
        return user;
    }
    
    // 类级别注解，整个类的方法都使用从数据源
    @Service
    @DataSourceSwitch("slave2")
    public class ReadOnlyUserService {
        @Autowired
        private UserRepository userRepository;
        
        public List<User> getAllUsers() {
            return userRepository.findAll();
        }
    }
}
```

#### 4.4.2 编程式数据源切换

使用`DataSourceContextHolder`编程式切换数据源：

```java
import com.bone.metadata.sdk.support.dataSource.DataSourceContextHolder;
import org.springframework.stereotype.Service;

@Service
public class DynamicDataSourceService {
    
    @Autowired
    private UserRepository userRepository;
    
    public User getUserWithDynamicDataSource(Long id, String dataSourceName) {
        try {
            // 切换到指定数据源
            DataSourceContextHolder.setDataSource(dataSourceName);
            // 执行数据库操作
            return userRepository.findById(id);
        } finally {
            // 清理数据源上下文，恢复默认数据源
            DataSourceContextHolder.clearDataSource();
        }
    }
    
    // 使用DataSourceManager的便捷方法
    @Autowired
    private DataSourceManager dataSourceManager;
    
    public User getUserWithManager(Long id, String dataSourceName) {
        return dataSourceManager.executeWithDataSource(dataSourceName, 
            () -> userRepository.findById(id));
    }
}
```

## 5. 高级功能

### 5.1 分页查询

SDK提供了内置的分页查询支持：

```java
// 使用PageParam进行分页
PageParam pageParam = new PageParam();
pageParam.setPageNumber(1);
pageParam.setPageSize(10);

Criteria<User> criteria = Criteria.<User>create()
    .eq("status", 1);

PageResult<User> pageResult = userRepository.findByCriteriaWithPage(criteria, pageParam);

// 获取分页信息
long total = pageResult.getTotal();
int pageCount = pageResult.getTotalPages();
List<User> data = pageResult.getData();
```

### 5.2 排序查询

支持多字段排序：

```java
// 使用SortableParam进行排序
SortableParam sortableParam = new SortableParam();
List<SortingField> sortingFields = new ArrayList<>();
sortingFields.add(new SortingField("createTime", "desc"));
sortingFields.add(new SortingField("name", "asc"));
sortableParam.setSortingFields(sortingFields);

List<User> sortedUsers = userRepository.findAll(sortableParam);

// 分页+排序
SortablePageParam sortablePageParam = new SortablePageParam();
sortablePageParam.setPageNumber(1);
sortablePageParam.setPageSize(10);
sortablePageParam.setSortingFields(sortingFields);

PageResult<User> sortedPageResult = userRepository.findAll(sortablePageParam);
```

### 5.3 批量操作

SDK支持批量插入、更新和删除操作，提高性能：

```java
// 批量插入
List<User> userList = new ArrayList<>();
// 添加多个用户...
userRepository.batchInsert(userList);

// 批量更新
List<User> usersToUpdate = userRepository.findByIds(Arrays.asList(1L, 2L, 3L));
for (User user : usersToUpdate) {
    user.setStatus(2);
}
userRepository.batchUpdate(usersToUpdate);

// 批量保存
List<User> usersToSave = new ArrayList<>();
// 添加新用户和更新的用户...
userRepository.batchSave(usersToSave);
```

### 5.4 自定义SQL查询

对于复杂查询，可以使用自定义SQL：

```java
// 使用命名SQL
Map<String, Object> params = new HashMap<>();
params.put("departmentId", 1001);
params.put("minAge", 18);

List<User> users = userRepository.executeNamedQuery("findUsersByDepartmentAndAge", params);

// 分页命名SQL
PageParam pageParam = new PageParam(1, 10);
PageResult<User> pageResult = userRepository.executePagedNamedQuery("findUsersByDepartmentAndAge", params, pageParam);
```

## 6. 配置参考

### 6.1 数据源配置

```yaml
# 基本数据源配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/master_db?useSSL=false
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver
    
    # 动态数据源配置
    dynamic:
      primary: master  # 默认数据源
      strict: false    # 非严格模式，数据源不存在时使用默认数据源
      datasource:      # 多个数据源配置
        master:
          url: jdbc:mysql://localhost:3306/master_db?useSSL=false
          username: root
          password: root
          driver-class-name: com.mysql.cj.jdbc.Driver
        slave1:
          url: jdbc:mysql://localhost:3306/slave_db1?useSSL=false
          username: root
          password: root
          driver-class-name: com.mysql.cj.jdbc.Driver

# Bone Metadata SDK 配置
bone:
  metadata:
    datasource:
      enabled: true      # 启用多数据源支持
      strict-mode: false # 数据源查找严格模式
      
      # 读写分离配置
      read-write:
        enabled: true
        master: master
        slaves:
          - slave1
          - slave2
        strategy: round-robin  # 负载均衡策略：round-robin, random, weight
        
      # 连接池配置
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5
        max-wait: 60000

# SQL 配置
metadata:
  sdk:
    sql:
      max-page-size: 1000
      default-page-size: 10
      batch-size: 1000
      cache-enabled: true
      cache-size: 1000
```

### 6.2 自动配置类

SDK提供了多个自动配置类，用于配置不同的组件：

- `DynamicDataSourceAutoConfiguration`：配置动态数据源
- `MultiDataSourceAutoConfiguration`：配置多数据源管理
- `QueryBuilderAutoConfiguration`：配置查询构建器
- `SqlRepositoryAutoConfiguration`：配置SQL仓库
- `InterceptorAutoConfiguration`：配置拦截器
- `MetadataAutoConfiguration`：配置元数据服务

## 7. 最佳实践

### 7.1 Repository模式

- 创建清晰的Repository接口，定义业务相关的数据访问方法
- 使用`BaseRepository`作为基类，继承通用CRUD操作
- 将复杂查询逻辑封装在Repository中，保持Service层的简洁

### 7.2 多数据源使用

- 对于读多写少的场景，使用主从架构，写操作走主库，读操作走从库
- 使用`@DataSourceSwitch`注解在方法级别控制数据源，避免在代码中硬编码
- 对于复杂的业务场景，考虑使用`DataSourceManager.executeWithDataSource`方法，确保数据源在异常情况下也能正确恢复

### 7.3 查询优化

- 使用`Criteria`或`QueryBuilder`构建查询条件，避免SQL注入
- 合理使用分页和排序，避免查询过多数据
- 对于复杂查询，考虑使用命名SQL或原生SQL
- 使用索引优化查询性能

### 7.4 事务管理

- 在Service层使用`@Transactional`注解管理事务
- 注意事务与多数据源的结合使用，确保事务一致性
- 对于跨数据源的事务，考虑使用分布式事务或最终一致性方案

### 7.5 错误处理

- 使用SDK提供的异常体系，如`QueryExecutionException`、`MultipleResultsException`等
- 在Service层捕获并处理数据访问异常，转换为业务异常
- 记录详细的错误日志，便于问题排查

## 8. 故障排除

### 8.1 数据源切换失败

- 检查数据源名称是否正确配置
- 检查`@DataSourceSwitch`注解是否正确使用
- 检查`DataSourceContextHolder`的调用是否在`try-finally`块中，确保资源正确清理
- 检查事务与数据源切换的结合使用是否正确

### 8.2 查询性能问题

- 检查SQL执行计划，优化查询语句
- 添加适当的索引
- 减少不必要的关联查询
- 使用分页查询，避免一次性查询过多数据
- 考虑使用缓存优化频繁查询的数据

### 8.3 事务一致性问题

- 确保事务边界设置正确
- 检查事务隔离级别是否适合业务场景
- 对于跨数据源的事务，考虑使用分布式事务框架
- 对于非关键操作，考虑使用最终一致性方案

## 9. 总结

Bone Metadata SDK 提供了强大而灵活的数据访问和元数据管理能力，支持多种数据源配置和查询方式。通过本指南，您可以快速上手并掌握SDK的核心功能，构建高效、可靠的数据访问层。在实际使用中，建议结合业务场景和最佳实践，充分发挥SDK的优势。