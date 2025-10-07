# **Bone SmartMeta 智能元数据引擎：完整企业级实施方案**

> **版本：1.0**  
> **目标：** 构建一个融合 Salesforce、Workday、Coupa Procurement 元数据管理精髓的、开箱即用、低门槛、高扩展的企业级智能元数据平台。

---

## **一、 项目背景与愿景**

### **1.1 企业数字化挑战**
现代企业面临系统复杂、变更频繁、业务与技术脱节的挑战。传统开发模式周期长、成本高，难以快速响应业务需求。Salesforce、Workday、Coupa 等 SaaS 平台通过强大的元数据驱动架构，实现了“配置即开发”，但往往价格昂贵且定制化受限。

### **1.2 Bone SmartMeta 的使命**
Bone SmartMeta 旨在**将 Salesforce 的“对象-字段”模型、Workday 的“流程即规则”思想、Coupa 的“合规性驱动”工作流**，融合到一个开源、可扩展、基于 Spring 生态的元数据引擎中。

**愿景：**
*   **开发者**：告别重复的 CRUD，通过声明式配置快速构建应用。
*   **业务用户**：通过可视化界面参与系统配置，实现“无代码”调整。
*   **企业**：获得一个可治理、可审计、可扩展的“企业元数据操作系统”。

---

## **二、 核心设计理念**

| 理念 | 说明 |
| :--- | :--- |
| **元数据即代码 (Metadata-as-Code)** | 使用 YAML、DSL 等声明式语言定义系统，支持 Git 版本控制、代码审查、CI/CD 流水线。 |
| **低代码/无代码 (Low-Code/No-Code)** | 提供 CLI、API 和未来 Web 界面，让非开发者也能参与系统配置。 |
| **企业级治理 (Enterprise Governance)** | 内置安全、审计、变更管理、多租户、合规性检查，满足企业 IT 治理要求。 |
| **动态可扩展 (Dynamic & Extensible)** | 支持运行时修改数据模型、业务规则、UI 布局，无需重启应用。 |
| **AI 增强 (AI-Enhanced)** | 集成 AI 模型，提供字段优化、索引建议、合规风险预警等智能建议。 |

---

## **三、 架构设计**

### **3.1 整体架构图**

```
+------------------+     +------------------+     +------------------+
|                  |     |                  |     |                  |
|   Web UI         |<--->|   SmartMeta      |<--->|   External       |
|   (Vue/React)    |     |   Engine         |     |   Systems        |
|                  |     |                  |     |   (SAP, Coupa...)|
+------------------+     +------------------+     +------------------+
                             ^     ^
                             |     |
                    +--------+     +--------+
                    |                        |
                    v                        v
        +------------------+       +------------------+
        |                  |       |                  |
        |   CLI Tool       |       |   API Client     |
        |   (smartmeta)    |       |   (Java/Python)  |
        |                  |       |                  |
        +------------------+       +------------------+
                             |
                             v
                    +------------------+
                    |                  |
                    |   Metadata       |
                    |   Repository     |
                    |   (Git / DB)     |
                    |                  |
                    +------------------+
```

### **3.2 模块化仓库结构**

```bash
bone-smartmeta/
├── smartmeta-engine/             # 核心引擎（元数据解析、公式、流程、安全）
├── smartmeta-starter/            # Spring Boot Starter（自动装配）
├── smartmeta-cli/                # 命令行工具（包管理、部署、测试）
├── smartmeta-ai/                 # AI 增强模块（建议、优化）
├── smartmeta-web/                # Web 管理界面（未来）
├── smartmeta-packages/           # 元数据包
│   ├── com.bone.crm/             # CRM 模块包
│   ├── com.bone.hr/              # HR 模块包
│   └── com.bone.procurement/     # 采购模块包
├── change-sets/                  # 变更集（环境推广）
├── docs/                         # 文档
└── samples/                      # 示例应用
```

---

## **四、 核心功能实现**

### **4.1 元数据包 (Smart Package)**

**文件：`smartmeta-packages/com.bone.procurement/smartpkg.yaml`**

```yaml
name: com.bone.procurement
version: 1.0.0
type: managed                 # managed/unmanaged
title: "采购管理"
description: "包含采购订单、供应商、审批流等元数据"
dependencies:
  - name: com.bone.core
    version: ">=1.0.0"
visibility:
  objects: read
  fields: read
  formulas: execute-only
install:
  preflight:
    - check: db_version >= 14
    - check: feature_flag('smartmeta.workflow_v2')
  steps:
    - apply: objects/*
    - apply: workflows/*
    - apply: security/*
upgrade:
  strategy: safe
  data-migrations:
    - script: migrations/1.1.0/add_tax_rate_field.sql
```

### **4.2 对象与字段定义**

**文件：`objects/PurchaseOrder.yaml`**

```yaml
apiName: PurchaseOrder
label: 采购订单
pluralLabel: 采购订单
table: sm_purchase_order
ownership: Private
recordTypes:
  - apiName: Direct
    label: 直采
  - apiName: Indirect
    label: 间采
fields:
  - name: Number
    type: AutoNumber
    pattern: "PO-{YYYY}{MM}{seq:5}"
    unique: true
  - name: Title
    type: Text
    length: 200
    required: true
  - name: Amount
    type: Currency
    scale: 2
    precision: 18
    required: true
  - name: VendorId
    type: Lookup(Vendor)
    required: true
  - name: Status
    type: Picklist
    values: [Draft, Submitted, Approved, Rejected, Closed]
    default: Draft
validationRules:
  - name: AmountPositive
    errorMessage: "金额必须为正数"
    expression: "Amount > 0"
fieldLevelSecurity:
  - profile: Buyer
    readable: [Title, Amount, VendorId, Status]
    editable: [Title, Amount, VendorId]
  - profile: Approver
    readable: [Title, Amount, VendorId, Status]
    editable: [Status]
```

### **4.3 业务流程与审批流**

**文件：`workflows/po-approval.yaml`**

```yaml
process: POApproval
version: 1
object: PurchaseOrder
entryCriteria: "Status == 'Submitted'"
steps:
  - name: BuyerApproval
    type: approval
    approver:
      type: Role
      role: "Buyer"
    sla:
      duration: "PT48H"
  - name: AmountGate
    type: decision
    condition: "Amount >= 10000"
    onTrue:
      - name: CFOApproval
        type: approval
        approver:
          type: Role
          role: "CFO"
    onFalse:
      - name: AutoApprove
        type: system
        action: "set(Status, 'Approved')"
exitActions:
  onApproved:
    - action: "set(Status, 'Approved')"
    - action: "emitEvent('PO_APPROVED')"
  onRejected:
    - action: "set(Status, 'Rejected')"
    - action: "notifyRequester('Your PO has been rejected.')"
```

### **4.4 公式与计算字段**

**文件：`fields/PurchaseOrder.calculated.yaml`**

```yaml
formulas:
  - name: AmountWithTax
    type: Currency
    precision: 18
    scale: 2
    expression: "Amount * (1 + OrgSettings.TaxRate('DEFAULT'))"
    recalculation:
      triggerOn: [Amount, RecordTypeId]
      mode: incremental
  - name: IsHighValue
    type: Boolean
    expression: "Amount >= OrgSettings.Threshold('HIGH_VALUE_PO')"
```

### **4.5 安全与共享规则**

**文件：`security/sharing-rules.yaml`**

```yaml
sharingRules:
  - name: MyTeamCanView
    object: PurchaseOrder
    criteria: "Owner.Department == currentUser.Department"
    grant:
      read: Role('Buyer')
  - name: HighValuePORequiresCFO
    object: PurchaseOrder
    criteria: "IsHighValue == true"
    grant:
      read: Role('CFO')
      update: Role('CFO')
```

### **4.6 UI 布局与表单**

**文件：`ui/PurchaseOrder.layout.yaml`**

```yaml
layout:
  sections:
    - label: 基本信息
      columns: 2
      fields:
        - Title
        - Number
        - Amount
        - VendorId
    - label: 审批信息
      columns: 1
      fields:
        - Status
        - AmountWithTax
  quickActions:
    - SubmitForApproval
    - Clone
```

### **4.7 外部系统集成**

**文件：`integrations/sap-export.yaml`**

```yaml
connector: SAP
object: PurchaseOrder
trigger: onApproved
mapping:
  - source: Number
    target: PO_NUMBER
  - source: Title
    target: DESCRIPTION
  - source: AmountWithTax
    target: TOTAL_AMOUNT
delivery:
  auth: BasicAuth(userRef: "SAP_USER")
  endpoint: "https://api.sap.com/procurement"
  method: POST
```

### **4.8 变更集与环境推广**

**文件：`change-sets/2025-10-20-po-fix/changeset.yaml`**

```yaml
name: po-layout-and-approval-fix
sourcePackage: com.bone.procurement@1.0.0
targetEnv: stage
checks:
  - compileFormulas
  - validateSecurityCoverage
  - runTests: ["po-rules.spec.yaml"]
diffs:
  - ui/PurchaseOrder.layout.diff.yaml
  - workflows/po-approval.diff.yaml
rollbackPlan:
  - restore: ui/PurchaseOrder.layout.yaml@0.9.0
  - restore: workflows/po-approval.yaml@1
```

---

## **五、 开发与运维流程**

### **5.1 开发者工作流**

```bash
# 1. 初始化项目
smartmeta init my-enterprise-app

# 2. 安装采购模块
smartmeta pkg install smartmeta-packages/com.bone.procurement

# 3. 创建变更集
smartmeta changeset create po-enhancement

# 4. 编辑元数据（如修改布局）
# ... 编辑 ui/PurchaseOrder.layout.yaml ...

# 5. 验证变更
smartmeta changeset validate change-sets/po-enhancement

# 6. 运行测试
smartmeta test change-sets/po-enhancement/tests

# 7. 推广到预发环境
smartmeta changeset apply change-sets/po-enhancement --env stage
```

### **5.2 Java 代码集成**

```java
@Service
public class POService {
    @Autowired
    private SmartMetaClient client;

    public void submitForApproval(String poId) {
        client.workflow("POApproval").start(poId);
    }

    public List<PurchaseOrder> findHighValuePOs() {
        return client.query("PurchaseOrder")
            .where("IsHighValue").eq(true)
            .orderBy("Amount").desc()
            .execute();
    }
}
```

### **5.3 Spring Boot 配置**

```yaml
# application.yml
smartmeta:
  packages:
    locations:
      - classpath:/smartmeta-packages/
  governance:
    approvals: required
    audit: full
  multitenancy:
    mode: schema
  formula:
    engine: v2
  workflow:
    engine: camunda
```

---

## **六、 采用路线图**

| 阶段 | 时间 | 目标 |
| :--- | :--- | :--- |
| **Phase 1** | 2周 | 基础对象、字段、UI、权限、代码生成、包安装。 |
| **Phase 2** | 4-6周 | 流程引擎、共享规则、多租户、变更集、审计。 |
| **Phase 3** | 6-10周 | 外部系统集成、安全域、AI 建议、合规报表。 |
| **Phase 4** | 10-12周 | Web 管理界面、AppExchange 市场、沙箱环境。 |

---

## **七、 总结**

Bone SmartMeta 不仅仅是一个技术框架，更是一套**企业级应用构建的方法论**。它通过**声明式、元数据驱动**的方式，实现了：

*   **极高的开发效率**：通过配置快速构建 CRUD 应用。
*   **强大的业务适应性**：业务用户可参与系统调整。
*   **完善的企业治理**：内置安全、审计、变更管理。
*   **无限的扩展潜力**：支持动态扩展和外部集成。

通过实施本方案，企业可以构建一个灵活、可扩展、易维护的数字化平台，真正实现“**像 Salesforce 一样敏捷，像 Spring 一样自由**”。