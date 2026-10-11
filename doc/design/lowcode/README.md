# 低代码最小闭环 · 专题包（Mode B）

> **本目录**收拢 Mode B「建模即页面 · 发布即生效」的**产品 PRD + 数据面设计 + UI/case 资产**。  
> **API 契约真源**仍在 [`doc/architecture/openapi/metadata-runtime-v1.yaml`](../../architecture/openapi/metadata-runtime-v1.yaml)。  
> **元数据模块详设**仍在 [`../modules/2. 元数据管理模块详细设计方案.md`](../modules/2.%20元数据管理模块详细设计方案.md)。  
> **平台主 PRD**仍在 [`doc/prd/BONE产品需求文档正式版.md`](../../prd/BONE产品需求文档正式版.md)。

## 文档地图

| 路径 | 角色 |
|------|------|
| [`BONE-低代码最小闭环PRD.md`](./BONE-低代码最小闭环PRD.md) | **产品** Why / What / 验收 |
| [`BONE-低代码Runtime数据面设计.md`](./BONE-低代码Runtime数据面设计.md) | **数据面** CRUD + Describe/Invoke + 逃逸 |
| [`cases/`](./cases/) | 场景探针（报销 L0/L1） |
| [`ui/`](./ui/) | 融合方案 + 评审原型 + 走查图 |

```
doc/design/lowcode/
├── README.md
├── BONE-低代码最小闭环PRD.md
├── BONE-低代码Runtime数据面设计.md
├── cases/
└── ui/
```

## 推荐阅读顺序

1. [PRD](./BONE-低代码最小闭环PRD.md) §0.6 / §0.7 / §2.5.8–2.5.9  
2. [Runtime 数据面设计](./BONE-低代码Runtime数据面设计.md)  
3. [ui 融合方案](./ui/元数据工作区前端融合方案.md) + [原型](./ui/bone-metadata-ui-prototype.html)  
4. [cases 费用报销](./cases/费用报销系统-元数据建模方案.md)

## 旧路径

| 旧路径 | 新路径 |
|--------|--------|
| `doc/prd/BONE-低代码最小闭环PRD.md` | [`./BONE-低代码最小闭环PRD.md`](./BONE-低代码最小闭环PRD.md) |
| `doc/design/modules/元数据Runtime数据面-*.md` | [`./元数据Runtime数据面-….md`](./BONE-低代码Runtime数据面设计.md) |
| `doc/design/lowcode/runtime/元数据Runtime…` | 同上（`runtime/` 仅留跳转 README） |
| `doc/design/ui/*` · `doc/design/cases/*` | [`ui/`](./ui/) · [`cases/`](./cases/) |
