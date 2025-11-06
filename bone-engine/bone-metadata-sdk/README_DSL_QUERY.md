# DSL查询API使用指南

## 概述

本文档介绍了bone-metadata-sdk中新增的DSL查询API，这是基于业界最佳实践设计的流畅查询接口，允许开发者通过链式调用和Lambda表达式构建类型安全的查询。现在DSL查询功能已直接集成到Repository接口中，提供了极简的使用体验，并通过丰富的快捷方法进一步降低使用门槛。

## 设计理念

- **极简整合**：DSL查询能力直接集成到Repository接口，无需额外组件
- **最小侵入性**：通过接口默认方法实现，现有代码无需修改即可使用
- **类型安全**：利用Java泛型和Lambda表达式提供编译期类型检查
- **流畅API**：支持链式调用，提高代码可读性
- **完全兼容**：基于现有SqlExecutor构建，无缝集成到现有系统
- **标准设计**：遵循业界流行的查询构建器设计模式（如MyBatis-Plus、Spring Data JPA）
- **智能处理**：自动处理空值、空集合等边界情况，提高代码健壮性

## 核心API

### 1. 入口方法

```java
// 方法1：先获取查询构建器
FluentQuery<User> query = userRepository.query();

// 方法2：直接开始条件查询
List<User> users = userRepository.where(User::getStatus).eq("ACTIVE").list();
```

### 2. 条件查询

```java
// 基础等值查询
List<User> activeUsers = userRepository.where(User::getStatus).eq("ACTIVE").list();

// 多条件查询（AND连接）
List<User> result = userRepository.where(User::getStatus).eq("ACTIVE")
                                 .and(User::getCreateTime).gt(startDate)
                                 .and(User::getUserName).like("%test%")
                                 .list();

// 范围查询
List<User> rangeUsers = userRepository.where(User::getAge).between(18, 35).list();

// IN查询
List<User> inUsers = userRepository.where(User::getId).in(Arrays.asList(1L, 2L, 3L)).list();

// NULL值查询
List<User> nullUsers = userRepository.where(User::getEmail).isNull().list();
```

### 3. 排序和分页

```java
// 排序查询
List<User> sortedUsers = userRepository.where(User::getStatus).eq("ACTIVE")
                                      .orderByDesc(User::getCreateTime)
                                      .list();

// 分页查询
PageResult<User> page = userRepository.where(User::getDepartment).eq("技术部")
                                     .orderBy(User::getUserName)
                                     .page(1, 20) // 第1页，每页20条
                                     .page();    // 执行分页查询

// 限制结果数量
List<User> limitedUsers = userRepository.where(User::getStatus).eq("ACTIVE")
                                       .limit(100)
                                       .list();
```

### 4. 结果获取方法

```java
// 获取列表
List<User> list = userRepository.where(User::getStatus).eq("ACTIVE").list();

// 获取单个结果（使用Optional）
Optional<User> optional = userRepository.where(User::getId).eq(1L).findOne();

// 获取第一个结果
User first = userRepository.where(User::getStatus).eq("ACTIVE").first();

// 获取总数
long count = userRepository.where(User::getStatus).eq("ACTIVE").count();

// 判断是否存在
boolean exists = userRepository.where(User::getUserName).eq("admin").exists();

// 获取分页结果
PageResult<User> pageResult = userRepository.where(User::getDepartment).eq("技术部")
                                          .page(1, 20)
                                          .page();
```

## 使用示例

### 示例1：基本查询

```java
// 查询活跃用户，按创建时间倒序
List<User> activeUsers = userRepository.where(User::getStatus).eq("ACTIVE")
                                     .and(User::getIsDeleted).eq(false)
                                     .orderByDesc(User::getCreateTime)
                                     .limit(100)
                                     .list();
```

### 示例2：分页查询

```java
// 分页查询第2页，每页20条
PageResult<User> page = userRepository.where(User::getDepartment).eq("技术部")
                                     .orderBy(User::getUserName)
                                     .page(2, 20)
                                     .page();

// 使用简化方法进行分页
PageResult<User> page2 = userRepository.findAllPage(1, 10);

// 使用分页结果
List<User> content = page.getContent();
long total = page.getTotal();
int pageNumber = page.getPageNumber();
int pageSize = page.getPageSize();
```

### 示例3：复杂条件组合

```java
// 复杂条件查询
List<User> users = userRepository.where(User::getStatus).eq("ACTIVE")
                               .and(User::getAge).gt(18)
                               .and(User::getAge).lt(60)
                               .and(User::getUserName).like("张%")
                               .and(User::getCreateTime).gt(startDate)
                               .orderByDesc(User::getCreateTime)
                               .list();
```

### 示例4：计数查询

```java
// 统计各部门的用户数量
Map<String, Object> params = new HashMap<>();
params.put("department", "技术部");
params.put("status", "ACTIVE");

long techUsersCount = userRepository.where(User::getDepartment).eq("技术部")
                                  .and(User::getStatus).eq("ACTIVE")
                                  .count();

// 使用简化方法统计总数
long totalUsers = userRepository.countAll();
```



## 实现说明

DSL查询API的实现充分利用了Java 8+的特性：

1. **接口默认方法**：在`Repository`接口中定义默认方法，现有实现类自动获得DSL能力
2. **Lambda表达式**：利用函数式接口和Lambda提供类型安全的字段引用
3. **链式调用**：每个方法都返回相应的接口类型，支持流畅的链式调用
4. **委托模式**：DSL实现内部委托给现有的`SqlExecutor`，确保完全兼容
5. **自动装配**：通过Spring Boot自动配置确保QueryBuilder正确初始化

## 架构整合

本次整合采用了以下策略：

1. **接口扩展**：在`Repository<T, ID>`接口中添加默认方法，提供DSL查询入口
2. **实现类适配**：在`BaseRepository`中添加必要的实现方法，提供底层支持
3. **复用现有组件**：直接使用已有的`SqlExecutor`和`QueryBuilder`，无需重复造轮子
4. **简化使用路径**：用户无需关心底层实现，直接通过Repository实例开始查询

这种设计使得用户可以在现有代码库中无缝切换到DSL查询方式，同时保持向后兼容性。

## 最佳实践

1. **根据复杂度选择API**：
   - 简单查询：使用`where()`直接开始条件查询
   - 复杂查询：先获取`query()`对象，再逐步构建条件

2. **性能优化建议**：
   - 对于大数据量查询，始终使用分页
   - 使用`limit()`限制返回结果数量
   - 合理利用索引字段进行查询和排序

3. **代码风格建议**：
   - 保持链式调用的缩进一致，每个条件占一行
   - 复杂查询时添加适当注释
   - 优先使用类型安全的Lambda表达式而不是字符串字段名

## 兼容性说明

- **完全向后兼容**：现有的Criteria查询和其他查询方法继续正常工作
- **混合使用支持**：可以在同一个应用中混合使用DSL查询和传统查询方式
- **事务管理保留**：所有DSL查询操作自动继承Repository的事务特性
- **扩展字段支持**：与现有的扩展字段功能完全兼容

## 性能考虑

- **零性能损失**：DSL查询构建过程本身几乎没有性能开销
- **底层优化复用**：查询执行时完全复用现有的SQL构建和执行逻辑
- **缓存协同**：支持与现有缓存机制协同工作
- **Lambda解析优化**：使用高效的Lambda表达式解析机制，避免运行时反射开销

## 代码优化建议

1. **优先使用DSL查询**：对于新开发的功能，推荐使用DSL查询以提高代码可读性
2. **渐进式迁移**：对于现有代码，可以在维护时逐步迁移到DSL查询
3. **复杂查询封装**：将复杂的查询逻辑封装在Repository实现类的方法中
4. **避免过深链式调用**：链式调用不要超过5-7个方法，保持代码可读性
5. **利用Optional返回值**：优先使用返回Optional的方法，如singleOpt()和first()