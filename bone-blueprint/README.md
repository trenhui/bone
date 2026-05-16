# bone-blueprint

参考实现：**展示 Bone DDD 规范的「能力上限」**，不是业务模块的默认体积。

## 文档

- 权威约定：[doc/architecture/Bone-DDD-最终实践方案.md](../doc/architecture/Bone-DDD-最终实践方案.md)（**第一部分**业界原则 + **第二部分**Bone 落地；**附录 A** 废止旧「Blueprint v×」版本号）  
- 与主工程对齐：[doc/wiki/08-blueprint与主工程对齐.md](../doc/wiki/08-blueprint与主工程对齐.md)  
- 重构完成记录（历史清单）：[REFACTOR_PLAN.md](./REFACTOR_PLAN.md)  
- 测试说明：[TEST_GUIDE.md](./TEST_GUIDE.md)  
- **§23 极简 / 低成本**：新建业务应优先对齐 **§14.2 + P0（§12.1）**，按需再引入本模块里的演示能力。

## 本模块里「全量演示」包含什么

| 能力 | 用途 |
|------|------|
| **`@Capability` + `HandlerRegistry`（可选）** | 编排侧发现 Handler 元数据；**Adapter 直接调 Handler**，无强制 UseCase 门面，见方案 §20 |
| **扩展点** | 多实现价格计算器（VIP/企业/促销等） |
| **Feign + `InventoryGateway`** | ACL 出站调用示例 |
| **MQ / 定时任务 / RPC** | 入站适配器形态示例 |

无对应需求时，**不必**在新模块中复制上述结构。

## 若要「尽量简单」地抄一版

建议最小子集（示意，类名随域替换）：

1. `domain/{aggregate}/`：聚合根、实体、值对象、领域事件（按需）  
2. `domain/repository/`：写侧仓储接口（继承 SDK `Repository`，不堆查询方法）  
3. `application/command` + `application/query`：命令/查询与 Handler  
4. `adapter/web`：Controller、request/response DTO、Assembler  
5. `infrastructure/config`：元数据与 Spring 配置  

**先不要**：扩展点矩阵、Feign、MQ、Schedule、RPC、`@Capability` 注册表——等业务或集成真的需要再加。

## 构建与测试

```bash
mvn test -pl bone-blueprint
```
