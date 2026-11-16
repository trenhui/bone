## Purpose

规范承保主体主数据（`ClaimInsurer` / `t_claim_insurer`）的语义与配置面引用：主体是保单、保司码表、映射、字段绑定的挂靠锚点，编码为租户内唯一业务键；保单必须引用有效主体；提供主体管理面。

## ADDED Requirements

### Requirement: 主体注册与三态生命周期
系统 SHALL 支持注册承保主体（insurer_code 租户内唯一、insurer_name、类型 DIRECT/BROKER/AGENT、可选 parent_code 指向法人主体、credit_code 统一社会信用代码）；主体生命周期为 ACTIVE / DISABLED / TERMINATED 三态流转，MUST NOT 物理删除（终止合作=TERMINATED，非删记录）；insurer_code 注册后 MUST 不可修改。

#### Scenario: 成功注册主体
- **WHEN** 管理员提交唯一编码与名称的新主体
- **THEN** 系统创建主体，初始状态 ACTIVE，并返回主体完整信息

#### Scenario: 编码重复被拒
- **WHEN** 管理员提交与已有主体相同的编码
- **THEN** 系统拒绝创建并提示「主体编码已存在」

#### Scenario: 修改编码被拒
- **WHEN** 管理员尝试修改已存在主体的编码
- **THEN** 系统拒绝该操作并提示编码不可修改

#### Scenario: 终止合作不删除记录
- **WHEN** 管理员将合作终止的主体置为 TERMINATED
- **THEN** 主体记录保留、既有保单/映射/赔案引用不受影响，仅不可再被新保单引用

### Requirement: 保单引用有效主体
保单新建与编辑 SHALL 校验 bizIdentityCode 引用本租户内状态为 ACTIVE 的主体；引用不存在、DISABLED 或 TERMINATED 主体的保存 MUST 被拒绝并返回可读错误；存量保单的自由文本主体值保持可读。

#### Scenario: 选择非 ACTIVE 主体保存保单被拒
- **WHEN** 用户提交引用 DISABLED/TERMINATED 或不存在主体的保单
- **THEN** 系统拒绝保存并返回可读错误

#### Scenario: 存量保单不受收紧影响读取
- **WHEN** 存量保单的业务主体编码在主体主档中不存在
- **THEN** 保单仍可正常读取与展示，仅编辑保存时要求先对齐到有效主体

### Requirement: 主体管理面
系统 SHALL 提供主体分页列表（编码/名称关键字、状态筛选）与编辑、启停操作，展示关联保单数；保单新建/编辑表单 SHALL 以可搜索下拉展示 ACTIVE 主体（编码+名称），替代自由文本输入。

#### Scenario: 表单仅可选 ACTIVE 主体
- **WHEN** 用户打开保单新建表单
- **THEN** 主体下拉仅列出 ACTIVE 主体，支持按编码/名称搜索
