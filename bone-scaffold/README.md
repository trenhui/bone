# Bone框架脚手架工程

这是基于Bone框架设计理念创建的脚手架工程，采用DDD（领域驱动设计）架构风格。

## 项目结构

```
bone-scaffold/
├── pom.xml                            # Maven配置文件
├── README.md                          # 项目说明文档
└── src/
    └── main/
        ├── java/com/bone/demo/
        │   ├── BoneScaffoldApplication.java  # 应用程序入口
        │   ├── adapter/
        │   │   └── web/            # Web层（控制器）
        │   │       └── UserController.java
        │   ├── application/        # 应用层
        │   │   ├── UserApplicationService.java
        │   │   ├── converter/      # DTO转换器
        │   │   │   └── UserConverter.java
        │   │   └── dto/            # 数据传输对象
        │   │       ├── UserDTO.java
        │   │       └── query/      # 查询DTO
        │   │           ├── UserQuery.java
        │   │           └── UserPageQuery.java
        │   ├── domain/             # 领域层
        │   │   ├── model/          # 领域模型
        │   │   │   └── User.java
        │   │   ├── repository/     # 仓储接口
        │   │   │   └── UserRepository.java
        │   │   └── service/        # 领域服务
        │   │       └── UserService.java
        │   └── infrastructure/     # 基础设施层
        │       └── config/         # 配置和工具类
        │           ├── ApiResponse.java
        │           └── PageResult.java
        └── resources/
            └── application.properties  # 应用配置
```

## 技术栈

- Java 17
- Spring Boot 3.2.1
- Lombok 1.18.30
- MapStruct 1.5.5.Final
- SpringDoc OpenAPI 2.2.0 (Swagger)

## 快速开始

### 1. 编译项目

```bash
mvn clean install
```

### 2. 运行应用

```bash
java -jar target/bone-scaffold-1.0.0.jar
```

或者使用Spring Boot Maven插件：

```bash
mvn spring-boot:run
```

### 3. 访问应用

- 应用地址：http://localhost:8080
- API文档：http://localhost:8080/swagger-ui.html

## 功能说明

### 用户管理API

- `POST /api/user/create` - 创建用户
- `GET /api/user/get?id={id}` - 获取用户信息
- `PUT /api/user/update` - 更新用户信息
- `DELETE /api/user/delete?id={id}` - 删除用户
- `GET /api/user/list` - 查询用户列表
- `GET /api/user/page` - 分页查询用户

## 扩展说明

### 添加新的业务模块

1. 在domain/model下创建新的领域模型
2. 在application/dto下创建对应的DTO类
3. 创建相应的Repository、Service、ApplicationService
4. 创建Controller暴露API接口
5. 在配置文件中添加必要的配置

### 注意事项

1. 本脚手架工程是示例项目，实际使用时需要：
   - 添加数据库连接配置
   - 实现具体的Repository接口（如使用MyBatis或JPA）
   - 添加业务日志、异常处理等横切关注点
   - 根据实际需要调整API设计

2. 代码生成功能可以通过bone-codegen模块自动生成类似的代码结构，提高开发效率。