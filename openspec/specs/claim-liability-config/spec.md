# claim-liability-config Specification

## Purpose
TBD - created by archiving change claim-liability-limit-negative-reject. Update Purpose after archive.

## Requirements

### Requirement: 责任额度上限的合法取值与「不封顶」表达

责任配置写入（新建 / 编辑）SHALL 拒绝负数的责任额度上限；`0` 与未填写 SHALL 保持合法，
且二者语义一致——**不封顶**。写入期 SHALL 以「负数被按「不封顶」解释 ⇒ 等于取消限额」
为理由给出可操作错误消息（指明不封顶应填 `0` 或留空）。

该守卫 SHALL 覆盖**合并后**的生效值（编辑接口的空字段沿用库里原值，负数可能从存量沿用下来），
不得只校验命令本身。

#### Scenario: 负数责任额度上限被拒
- **WHEN** 调用建责任或编责任接口，`liability_limit` 为负数
- **THEN** 返回 4xx，错误消息指明该字段不能为负、负数会被按「不封顶」解释，不封顶应填 0 或留空
- **AND** 该责任不被落库（不产生草稿行）

#### Scenario: 0 与未填写均表示「不封顶」
- **WHEN** 建责任时 `liability_limit` 为 `0`
- **THEN** 请求成功，落库值为 0，语义为不封顶
- **AND** WHEN `liability_limit` 未填写
- **THEN** 同样请求成功并落库为 0（未填写归一为 0）

#### Scenario: 正数额度正常封顶
- **WHEN** 建责任时 `liability_limit` 为正数
- **THEN** 请求成功，落库值为该正数，责任额度按此封顶

### Requirement: 责任额度上限负数的存量归一化

存量数据修复 SHALL 将 `liability_limit < 0` 的行归一为 `0`，并 SHALL 保证归一化前后
**理算结果完全一致**（负数与 0 在理算期同义，均为「不封顶」）。

修复 SHALL 同步递增乐观锁版本，避免仍持有旧版本的应用实例用陈旧对象覆盖修复结果；
SHALL 幂等（重复执行影响 0 行）；SHALL 在执行末尾以终态断言 fail-fast
（仍存在负数行 ⇒ 说明仍有写入路径漏掉守卫）。

#### Scenario: 存量负数被归一且赔付不变
- **WHEN** 迁移脚本在含 `liability_limit < 0` 行的库上执行
- **THEN** 这些行变为 0，其 `version` 递增 1
- **AND** 这些责任在归一化前后的理算赔付金额完全相同（此前均为「不封顶」）

#### Scenario: 迁移幂等
- **WHEN** 迁移脚本重复执行
- **THEN** 第二次影响 0 行，终态断言通过
