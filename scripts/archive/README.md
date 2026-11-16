# scripts/archive/ —— 归档区（只进不出，不参与任何门禁）

> **归档 ≠ 删除**。这些脚本没有被 `scripts/` 下任何活入口（`check.sh` / `ci-check.sh` / `ci-local.sh` / `ci.yml` / `run.sh`）调用，
> 也不再是任何人复跑的常规路径；但历史报告与设计文档里可能引用它们，故保留本体以便追溯。
>
> **本目录不参与门禁执行**：不 lint、不跑单测、不被任何活入口调用；
> 但**仍须编目登记**——`scripts/tests/test_scripts_catalog.py` 的 R1 会校验本目录下每个脚本都在本文清单里，
> 避免归档区变成无人清点的第二堆积场。恢复使用请先证明它仍是活需求，
> 并按 [`../README.md` §9](../README.md) 接回正式入口——而不是直接从本目录被引用。

## 为什么归档（2026-11-11 编目整合）

诊断脚本目录时发现两类「住在可复用库里的一次性资产」，与 `scripts/e2e/` 前几轮整合掉
`diag-*` / `debug-form.mjs` 是同一形态：

1. **一次性联调探针**：硬编码某次发布的实体编码或某次交互细节，针对瞬时状态「快速实证」；
   不能被复跑比对，却让后来者误以为它们是权威入口。
2. **被取代的老 curl 验证脚本**：`verify_*.sh` 一类的 `TOKEN` 入参 + `curl` 逐端点清单；
   能力已被 `scripts/e2e/`（`preflight` + 分组链路 + 退出码契约 + JUnit）覆盖。

## 清单

| 文件 | 原位置 | 归档理由 |
|---|---|---|
| `_e2e_uischema.js` | `scripts/` | 界面设计器一次性 Playwright 探针（重命名/引导/全屏/Cmd+K/FAB 实证），仅被 `outputs/` 历史报告引用 |
| `_probe_designer_polish.js` | `scripts/` | 设计器打磨回归一次性探针（19 项），同上 |
| `_probe_p1_contract.js` | `scripts/` | P1 list 契约 vs 运行时列一次性探针，硬编码理赔案件 ID |
| `_probe_p5_publish_visibility.js` | `scripts/` | P5 发布可见性一次性探针，依赖特定自造 pending 现场 |
| `_probe_p6_negative.js` | `scripts/` | P6 负向探针，用于反证某次「自检恒 0」缺陷；缺陷已修，证明使命结束 |
| `_probe_p6_onboarding.js` | `scripts/` | P6 新手指引 + 三实体 0-1 人工串测一次性探针 |
| `_probe_scenario_filter.js` | `scripts/` | 场景筛选一次性探针，全仓零引用 |
| `tmp-iam-gateway-rename.py` | `scripts/` | 文件名自带 `tmp-`；IAM→gateway 路由改名一次性 codemod，全仓零引用 |
| `verify_all.sh` | `scripts/` | 早期全量端点验证（`TOKEN` 入参 + curl 清单），全仓零引用，能力由 `e2e/` 覆盖 |
| `verify_iam.sh` | `scripts/` | 同上，IAM 单域版本 |
| `endpoint-test.sh` | `scripts/` | 「基于 Controller 源码手抄的 API 清单」v4，硬编码路径；零引用 |

## 为什么不是直接删

与 `e2e/` 第二轮「确认无复用价值后整体删除」的区别在于**证据等级**：
那批脚本在删除前做过一次专门的复用价值复核；本批仅完成了「零引用 + 命名/形态可判定为一次性」的
机器证据，尚未经各域 owner 逐个人工确认。归档是可逆的第一步，删除应由 owner 在下一轮决定。

## 已知的过期路径引用（不改历史证据）

`outputs/ui-schema-designer-comparison.md` 与 `outputs/s1-dual-track-integration-verification.md`
是当时的联调报告，正文里写着 `scripts/_probe_*.js` 等原路径。**报告属历史证据，不改写**；
按本文档的「清单」表换算到 `scripts/archive/` 即可。其余已归档脚本全仓零引用。

