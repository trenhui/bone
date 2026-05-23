# Bone Blueprint Go

基于 Go 语言的订单管理系统示例，使用 bone-extension-go 和 bone-metadata-go。

## 项目结构

```
bone-blueprint-go/
├── adapter/              # 适配器层
│   ├── mq/               # MQ 监听器
│   ├── rpc/              # RPC 服务
│   ├── schedule/         # 定时任务
│   └── web/              # Web 控制器
├── application/          # 应用层（Controller 直注 Handler，无 usecase 包）
│   ├── command/          # 命令：cmd + handler
│   ├── event/            # 事件处理
│   └── query/            # 查询：qry + handler + dto
├── domain/               # 领域层
│   ├── exception/        # 领域异常
│   ├── extension/        # 扩展点
│   ├── gateway/          # 网关
│   ├── order/            # 订单领域
│   ├── repository/       # 仓储接口
│   └── security/         # 安全
├── infrastructure/       # 基础设施层
│   ├── config/           # 配置
│   ├── extension/        # 扩展实现
│   ├── gateway/          # 网关实现
│   ├── handler/          # 处理器
│   └── security/         # 安全实现
├── main.go               # 应用入口
├── go.mod                # Go 模块
└── config.yml            # 配置文件
```

## 技术栈

- **语言**: Go 1.21+
- **Web 框架**: Gin
- **ORM**: 使用 bone-metadata-go
- **扩展**: 使用 bone-extension-go
- **安全**: JWT
- **缓存**: Redis
- **消息队列**: Kafka

## 快速开始

```bash
# 安装依赖
go mod tidy

# 运行应用
go run main.go
```

## 核心功能

1. **订单管理**
   - 创建订单
   - 支付订单
   - 取消订单
   - 查询订单

2. **扩展系统**
   - 订单价格计算器扩展点
   - 多种价格计算策略

3. **领域事件**
   - 订单创建事件
   - 订单支付事件
   - 订单取消事件

4. **安全**
   - JWT 令牌认证
   - 密码加密

5. **集成**
   - 库存服务集成
   - MQ 消息处理
   - 定时任务

## 扩展点

### 订单价格计算器

- **默认实现**: DefaultOrderPriceCalculator
- **会员实现**: MemberOrderPriceCalculator
- **VIP 实现**: VipOrderPriceCalculator
- **企业实现**: EnterpriseOrderPriceCalculator
- **促销实现**: PromotionOrderPriceCalculator
