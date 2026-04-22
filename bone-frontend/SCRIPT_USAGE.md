# BONE 前端应用脚本使用指南

## 脚本说明

本目录包含两个主要脚本，用于管理BONE前端应用的启动和重启：

1. **start-all-apps.sh** - 批量启动所有前端应用
2. **restart-all-apps.sh** - 批量重启所有前端应用

## 端口配置

| 应用名称 | 端口 | 描述 |
|---------|------|------|
| bone-shell | 3000 | 主应用（Shell） |
| bone-iam-app | 3001 | IAM管理应用 |
| bone-metadata-app | 3002 | 元数据管理应用 |
| bone-masterdata-app | 3003 | 主数据管理应用 |
| bone-integration-app | 3004 | 集成管理应用 |
| bone-system-app | 3005 | 系统管理应用 |
| bone-extension-app | 3006 | 扩展管理应用 |

## 脚本使用方法

### 1. 启动所有应用

```bash
# 赋予脚本执行权限
chmod +x start-all-apps.sh

# 启动所有应用
./start-all-apps.sh start

# 查看应用状态
./start-all-apps.sh status

# 停止所有应用
./start-all-apps.sh stop

# 重启所有应用
./start-all-apps.sh restart
```

### 2. 重启所有应用

```bash
# 赋予脚本执行权限
chmod +x restart-all-apps.sh

# 重启所有应用
./restart-all-apps.sh all

# 仅重启主应用
./restart-all-apps.sh shell

# 仅重启IAM应用
./restart-all-apps.sh iam

# 仅重启元数据应用
./restart-all-apps.sh metadata

# 仅重启主数据应用
./restart-all-apps.sh masterdata

# 仅重启集成应用
./restart-all-apps.sh integration

# 仅重启系统应用
./restart-all-apps.sh system

# 仅重启扩展应用
./restart-all-apps.sh extension
```

## 日志管理

所有应用的日志都会输出到 `./logs` 目录中，每个应用有独立的日志文件：

- `logs/bone-shell.log`
- `logs/bone-iam-app.log`
- `logs/bone-metadata-app.log`
- `logs/bone-masterdata-app.log`
- `logs/bone-integration-app.log`
- `logs/bone-system-app.log`
- `logs/bone-extension-app.log`

## 注意事项

1. 确保所有应用的依赖已经安装（使用 `npm install`）
2. 确保端口 3000-3006 没有被其他应用占用
3. 脚本会自动创建日志目录和PID文件
4. 启动应用后，可以通过 `http://localhost:3000` 访问主应用
5. 所有微应用会通过主应用的菜单进行访问

## 故障排查

如果应用启动失败，可以查看对应应用的日志文件：

```bash
# 查看主应用日志
tail -f logs/bone-shell.log

# 查看IAM应用日志
tail -f logs/bone-iam-app.log
```

如果端口被占用，可以使用以下命令查看并释放：

```bash
# 查看端口占用情况
lsof -i :3000

# 释放端口（替换 <PID> 为实际进程ID）
kill -9 <PID>
```
