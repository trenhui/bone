# bone-extension-studio

扩展管理控制台（`/api/v1/extension/*`），**应用模块**（DDD 全量 P0）。

## DDD 约定

- 写侧：`domain/repository/*`（§18.2 白名单）
- 读侧：`domain/gateway/*ReadPort`（[ADR-0013](../../../doc/architecture/adr/0013-extension-studio-repository-read-side.md)）
- 实现：`infrastructure/persistence/*` 双接口（`implements XxxRepository, XxxReadPort`）
- ArchUnit：`src/test/java/.../architecture/ArchitectureTest.java`；`repository_methods_whitelist` 直接生效（不 freeze）
