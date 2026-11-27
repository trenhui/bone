# claim/secure-signing Specification

## Purpose
定义 Bone 理赔的正式签收能力：以签收批次为聚合，覆盖线上自动签收（内部报案与外部推送双触发）与线下人工签收两条通道，规定状态机、幂等、权限与留痕等对外可验证行为。

## Requirements

### Requirement: 签收批次聚合与批次号唯一

系统 SHALL 以签收批次为单位管理签收，每个批次 MUST 具备全局唯一批次号 `batchNo`、租户 `tenantId`、来源 `source`（`ONLINE`/`OFFLINE`）、签收方式 `signType`（`AUTO`/`MANUAL`）、签收渠道 `signChannel`、状态 `signStatus`、签收人/时间/意见；数据库 MUST 对 `batchNo` 建唯一索引，重复批次号 MUST 被拒绝。批次下 MUST 可挂多条签收明细（赔案），明细与批次为多对一。

#### Scenario: 创建批次生成唯一批次号
- **WHEN** 系统创建一个线上或线下签收批次
- **THEN** 自动生成符合编码规则的 `batchNo`（如 `SG` 前缀），且重复使用该号创建第二批 MUST 失败并返回明确错误码

#### Scenario: 线下批次携带物流要素
- **WHEN** 以 `source=OFFLINE` 创建批次并填写快递公司/单号/发件人/到件时间
- **THEN** 这些要素 MUST 随批次持久化并可在批次详情查询到；`source=ONLINE` 的批次 MUST 不要求这些要素

### Requirement: 签收批次四态状态机

批次状态 MUST 为 `WAIT_FOR_PARTICIPANT` / `WAIT_FOR_IMAGE` / `WAIT_FOR_CONFIRMATION` / `FINISHED` / `DELETED` 之一，迁移 MUST 遵循 `WAIT_FOR_PARTICIPANT → WAIT_FOR_IMAGE → WAIT_FOR_CONFIRMATION → FINISHED`，且每个迁移 MUST 校验前置状态；非法跃迁 MUST 被拒绝且不产生副作用。`FINISHED` 为终态，MUST 拒绝再次确认签收。

#### Scenario: 合法四步流转
- **WHEN** 批次依次完成创建、名单导入、影像上传、人工确认
- **THEN** 状态依次进入四个合法态，每次迁移成功后可查询到新状态

#### Scenario: 终态幂等拒绝
- **WHEN** 对已 `FINISHED` 的批次再次调用确认签收
- **THEN** 系统 MUST 拒绝并返回「已完成签收，无需重复签收」类错误，状态与轨迹不变

#### Scenario: 跳过前置步骤被拒绝
- **WHEN** 批次尚在 `WAIT_FOR_PARTICIPANT` 时直接请求确认签收
- **THEN** 系统 MUST 拒绝并说明前置条件未满足

### Requirement: 线上自动签收双触发

系统 SHALL 支持两条线上自动签收触发路径：① 内部报案提交成功后，在同一事务边界内自动创建 `source=ONLINE`、`signType=AUTO` 的签收批次并签收该案件；② 通过外部回执端点接收保险公司/CPL/TPA 推送的案件信息，幂等地自动建案并签收（`source=ONLINE`、`signType=AUTO`，`signChannel` 记录推送方标识）。两条路径 MUST 产生可查询的签收轨迹，且 MUST 不改变既有报案提交 API 的响应结构。

#### Scenario: 内部报案自动签收
- **WHEN** 用户提交一份内部报案且校验通过
- **THEN** 系统在报案落库后自动创建线上自动签收批次、签收该案件，`ACCEPTED` 状态与签收轨迹均可查询

#### Scenario: 外部推送幂等签收
- **WHEN** 外部系统以同一业务标识重复推送同一案件两次
- **THEN** 系统 MUST 只创建一条签收记录与一条批次，第二次请求返回第一次的结果（幂等成功），批次号不变

#### Scenario: 外部推送缺失必填要素
- **WHEN** 外部推送缺少案件标识或来源方标识
- **THEN** 系统 MUST 拒绝并返回参数校验错误码，不创建任何批次或案件

### Requirement: 线下人工批次签收

系统 SHALL 支持线下人工批次签收：创建批次 → 导入人员名单（生成明细，案件阶段 `SIGNING`）→ 登记影像上传 → 人工二次确认签收；确认时 MUST 校验所有明细影像完备，成功后批次置 `FINISHED`、明细案件阶段 `SIGNING → PRE_EXAM`（进入初审），并记录签收人、签收时间与签收意见。签收意见 MUST 非空且长度不低于系统配置下限（默认 10 字）。

#### Scenario: 完整线下签收流程
- **WHEN** 操作人员创建线下批次、导入人员名单、完成影像上传并提交非空且满足长度的签收意见确认
- **THEN** 批次状态变为 `FINISHED`，全部明细案件进入 `PRE_EXAM`，轨迹记录签收人/时间/意见

#### Scenario: 影像未完备拒绝确认
- **WHEN** 存在任一明细尚未完成影像上传时请求确认签收
- **THEN** 系统 MUST 拒绝并列出未完成影像的明细，批次状态不变

#### Scenario: 签收意见过短拒绝
- **WHEN** 确认签收时签收意见为空或少于配置下限
- **THEN** 系统 MUST 拒绝并返回校验错误，不产生状态迁移

### Requirement: 防重复签收与并发安全

签收确认 MUST 使用 CAS 条件更新（按前置状态过滤，例如 `WHERE sign_status = 'WAIT_FOR_CONFIRMATION'`），影响行数为 0 MUST 视为已被他人签收并返回冲突类错误；MUST NOT 使用 `SELECT ... FOR UPDATE`。所有签收写操作 MUST 在 REQUIRED 事务传播下执行，携带幂等键（稳定键 MUST 归一化到 64 字符以内）；同一幂等键的重复请求 MUST 返回首次结果。

#### Scenario: 并发确认只有一个成功
- **WHEN** 两个请求同时对同一 `WAIT_FOR_CONFIRMATION` 批次确认签收
- **THEN** 恰有一个成功置 `FINISHED`，另一个 MUST 收到冲突错误且无副作用

#### Scenario: 幂等键重试
- **WHEN** 客户端使用同一幂等键重试一次已成功的确认签收请求
- **THEN** 系统 MUST 返回首次成功结果，不重复迁移状态、不重复写轨迹

### Requirement: 权限与签收轨迹留痕

签收操作 MUST 校验当前用户对案件的处理人权限及案件未挂起状态；权限不足或案件挂起 MUST 拒绝。每一次签收相关状态迁移 MUST 追加写入不可变轨迹（含批次/明细、前后状态、动作类型、操作人、时间、备注），轨迹 MUST 只追加不更新。

#### Scenario: 无权限用户签收被拒
- **WHEN** 非处理人用户尝试确认签收
- **THEN** 系统 MUST 拒绝并返回权限错误，不产生状态迁移与轨迹

#### Scenario: 挂起案件签收被拒
- **WHEN** 案件处于挂起状态时尝试从其所属批次确认签收
- **THEN** 系统 MUST 拒绝并说明案件已挂起

#### Scenario: 轨迹完整可追溯
- **WHEN** 批次从创建到签收完成经历全部状态迁移
- **THEN** 轨迹 MUST 按时间顺序包含每次迁移记录，且记录不可被修改或删除

### Requirement: 签收工作台的前端可操作性

前端 MUST 提供「案件签收」页面，以线上/线下分栏展示批次列表（服务端真分页），支持新建批次、人员名单导入、影像上传入口、二次确认签收与轨迹查看；确认签收 MUST 有二次确认与进行中禁用（防重复点击）；入口 MUST 作为正式菜单出现在主导航，路由 `'/sign'`（完整路径 `/sclaim/claim/sign`），名称与 IAM 菜单逐字一致，并通过单导航 E2E 探针校验。

#### Scenario: 从菜单进入签收页面
- **WHEN** 具备签收权限的用户点击主导航「案件签收」
- **THEN** 页面成功打开 `/sclaim/claim/sign`，可见线上/线下分栏与批次列表

#### Scenario: 二次确认与防重复点击
- **WHEN** 用户在批次列表点击「确认签收」
- **THEN** 系统 MUST 弹出二次确认（含前置条件说明），提交期间按钮 MUST 禁用，成功后刷新列表与状态

#### Scenario: 列表筛选与分页保持 URL 契约
- **WHEN** 用户切换 Tab、筛选或翻页
- **THEN** 筛选条件与页码 MUST 记录在 URL（`q/page/size/f_*`），刷新与分享链接可还原视图
