# Bone 国际化（i18n）规范

> **文档性质**：控制台多语言、错误展示与时区格式的**前后端约定**。  
> **更新**：2026-05-17  
> **关联**：[Bone-错误码登记.md](./Bone-错误码登记.md)、[Bone-API-规范.md](./Bone-API-规范.md) §14.3（i18n；§14.1 为时间）

---

## 1. 原则

| 原则 | 说明 |
|------|------|
| 稳定键 | 后端 `errorCode`、前端 `i18n key` **不随文案变化** |
| 分离 | 展示文案在前端/资源包；后端 `message` 为中文 fallback |
| 禁止 | 修改已发布 `errorCode` 含义 |

---

## 2. 后端

| 项 | 规则 |
|----|------|
| API | 返回 `errorCode` + 默认 `message`（中文） |
| 成功 message | 可固定中文；非关键 |
| 枚举 | 对外字符串枚举：`ENABLED`（API §14.2） |
| 时间 | API 输出 **UTC ISO-8601**（`...Z`） |

---

## 3. 前端（bone-frontend）

| 项 | 规则 |
|----|------|
| 库 | `react-i18next` 或 Ant Design `ConfigProvider locale` |
| 键 | `error.EXT_PLUGIN_NOT_FOUND` 映射 `errorCode` |
| 回退 | 无翻译 → 显示后端 `message` → 显示 `errorCode` |
| 路由 | Shell 提供语言切换；子应用继承 locale |

目录建议：

```
packages/shared-utils/src/i18n/
  locales/zh-CN.json
  locales/en-US.json
```

---

## 4. 日期与时区

| 层 | 规则 |
|----|------|
| API | UTC  instant |
| 展示 | `dayjs`/`date-fns` 按用户时区格式化 |
| 禁止 | 后端按服务器时区格式化后当字符串返回 |

---

## 5. 数字与货币

- 大数、金额用 `Intl.NumberFormat`  
- 货币代码 ISO 4217（若业务需要）  

---

## 6. 检查清单

- [ ] 新 `errorCode` 已加 en-US（至少）文案键  
- [ ] 未硬编码中文到不可替换组件（关键错误）  
- [ ] 列表日期列用时区格式化  

---

## 7. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-17 | 从 API §14.3 独立 |
