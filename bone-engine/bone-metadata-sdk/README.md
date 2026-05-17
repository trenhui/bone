# bone-metadata-sdk

Bone 平台默认持久化栈：元数据驱动仓储、`Criteria` / DSL 读侧、SQL 模板与多数据源。与 [《Bone-DDD》持久化 P0](../../../doc/architecture/Bone-DDD-最终实践方案.md) 对齐。

## 文档（请优先阅读）

| 文档 | 用途 |
|------|------|
| [doc/使用指南.md](doc/使用指南.md) | 日常 API：仓储、Criteria、DSL、多数据源、排错 |
| [doc/Bone-Metadata-SDK-最佳实践方案.md](doc/Bone-Metadata-SDK-最佳实践方案.md) | 架构原则、读写分层、演进路线 |
| [doc/README.md](doc/README.md) | 文档索引 |

**事实来源**：`src/main/java` 与 `src/test/java`；勿在 `src/main/` 下新增 Markdown 草稿。

## 快速开始

**依赖**（版本以 `bone-parent` 为准）：

```xml
<dependency>
    <groupId>com.bone</groupId>
    <artifactId>bone-metadata-sdk</artifactId>
</dependency>
```

**启用仓储扫描**：

```java
@EnableSqlRepositories(basePackages = "com.bone.yourmodule.domain.repository")
@SpringBootApplication
public class Application { }
```

**定义仓储**（空接口 + D1 注解实体，勿继承 `BaseRepository`）：

```java
public interface UserRepository extends Repository<User, Long> { }

// 使用
User user = userRepository.findById(1L);
List<User> list = userRepository.where(User::getStatus).eq("ACTIVE").list();
```

**最小配置**：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/bone
    username: root
    password: ${BONE_DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

## 构建与测试

```bash
mvn -pl bone-engine/bone-metadata-sdk test
```

## 相关模块

| 模块 | 说明 |
|------|------|
| `bone-metadata-server` | 扩展字段 REST 控制面（:9001，选配） |
| `bone-metadata-engine` | 智能元数据引擎（计算面，选配） |
| `bone-datasource` | 动态多数据源（`bone-framework`） |

**三模块协作与竞品对照**：[doc/design/modules/元数据能力-实现映射与竞品对照.md](../../doc/design/modules/元数据能力-实现映射与竞品对照.md)
