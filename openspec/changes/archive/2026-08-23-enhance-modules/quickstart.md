# Quickstart: 增强模块

## 本地验证
```bash
# 扩展路由修正
cd bone-frontend/apps/bone-extension-app && npm run dev
# 文件服务
mvn -pl bone-platform/bone-file -am clean install -DskipTests
# 通知
mvn -pl bone-platform/bone-notification -am clean install -DskipTests
# 生成器
mvn -pl bone-engine/studio-generator -am clean install -DskipTests
```

## 关键入口
- 扩展路由：`bone-frontend/apps/bone-extension-app/src/router/`
- 文件：`bone-platform/bone-file/.../infrastructure/storage/FileStorageService.java`
- 通知：`bone-platform/bone-notification/.../domain/model/aggregate/NotificationMessage.java`
- 生成器：`bone-engine/studio-generator/.../generator/`
