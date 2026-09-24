# Nacos 配置中心接入指南

## 现状

Spring Cloud Alibaba BOM 已在 `bone-parent/pom.xml` 声明；**已有多模块实际接入**：`bone-iam`、`bone-system`、`bone-integration`、`bone-masterdata` 均含 `bootstrap.yml` 并引入 `spring-cloud-starter-alibaba-nacos-config`。其余模块仍通过 `application.yml` + 环境变量管理，可按需参照本文接入。

## 接入步骤

### 1. 添加依赖（在各微服务模块的 pom.xml 中）

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

### 2. 创建 bootstrap.yml

在各微服务的 `src/main/resources/` 下创建 `bootstrap.yml`：

```yaml
spring:
  application:
    name: bone-iam  # 替换为对应服务名
  profiles:
    active: ${BONE_PROFILE:dev}
  cloud:
    nacos:
      config:
        server-addr: ${BONE_NACOS_ADDR:localhost:8848}
        namespace: ${BONE_NACOS_NAMESPACE:}
        group: BONE
        file-extension: yaml
        # 共享配置（所有微服务公共配置）
        shared-configs:
          - data-id: bone-common.yaml
            group: BONE
            refresh: true
```

### 3. 在 Nacos 中创建配置

| Data ID | Group | 内容 | 说明 |
|---------|-------|------|------|
| bone-common.yaml | BONE | 数据源、Redis、JWT 等公共配置 | 所有微服务共享 |
| bone-iam.yaml | BONE | IAM 专属配置 | 仅 bone-iam 加载 |
| bone-system.yaml | BONE | System 专属配置 | 仅 bone-system 加载 |
| ... | ... | ... | 其他服务同理 |

### 4. bone-common.yaml 示例内容

```yaml
spring:
  datasource:
    url: jdbc:mysql://${BONE_DB_HOST:localhost}:3306/bone?useSSL=false&autoReconnect=true&characterEncoding=utf8&allowPublicKeyRetrieval=true
    username: ${BONE_DB_USER:root}
    password: ${BONE_DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
  redis:
    host: ${BONE_REDIS_HOST:localhost}
    port: ${BONE_REDIS_PORT:6379}
    password: ${BONE_REDIS_PASSWORD:}
    database: 0

bone:
  security:
    jwt:
      secret: ${BONE_JWT_SECRET}
      access-token-expiry: 30m
      refresh-token-expiry: 7d
```

### 5. 敏感配置加密

在 Nacos 中对密码等敏感字段使用内置加密：

```yaml
spring:
  datasource:
    password: ENC(加密后的密文)  # Nacos 配置加密
```

### 6. 本地开发（无 Nacos）

本地开发时，设置环境变量 `BONE_NACOS_ADDR=none` 或在 `application-local.yml` 中禁用：

```yaml
spring:
  cloud:
    nacos:
      config:
        enabled: false
```

## 注意事项

- **渐进式迁移**：可以先在 dev 环境启用 Nacos，验证无误后再推 prod
- **环境隔离**：使用 Nacos namespace 隔离 dev/test/prod
- **配置刷新**：使用 `@RefreshScope` 注解实现配置热更新
- **不回退**：启用 Nacos 后，原 application.yml 中的公共配置应移至 Nacos，避免双份维护
