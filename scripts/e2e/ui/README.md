# 前端 UI 巡检探针

> 目录级约定（公共层 / 新增规范 / 环境变量）见 [scripts/e2e/README.md](../README.md)；本文件只管浏览器探针。

无头 Chrome（Playwright）驱动的三个探针，覆盖"点击每一个功能"。**先起全环境**：

```bash
bash scripts/dev/up-all-frontend-e2e.sh          # 9 个 Vite dev server
bash scripts/dev/up-all-services-e2e.sh          # 10 个 Spring Boot 服务
```

## 三个探针的分工

| 探针 | 作用 | 关键判据 |
|---|---|---|
| `probe-buttons.mjs` | 采集各页面**真实**菜单项与按钮名 | 静态 grep 拿不全路由/按钮名，一切以运行时为准 |
| `probe-routes.mjs` | 逐菜单**点击**导航 + 断言业务元素 | `tableRows>0→DATA` / `empty→EMPTY` / `cards→CARDS` / `inputs→FORM` / 否则 `THIN`；同时收集 4xx/5xx 与 console error |
| `probe-write.mjs` | 写入口深度交互 | 点"新增" → 断言弹窗开（**Modal 与 Drawer 都认**）→ 空提交看校验 → 取消能关 |
| `probe-commerce.mjs` | **多渠道交易域写操作闭环** | 开弹窗→填表→提交→断言 antd message；30 项断言，见下节 |
| `claim-single-nav-probe.mjs` | **微前端单导航 + 菜单↔路由双向一致**（智能理赔域，74 项断言） | `.shell-sider` 恰 1 个且微应用内 0 个 `.ant-layout-sider`（双层菜单复发即红）；IAM 菜单 vs `routes.tsx` 的路径/名称/**顺序**全等（幽灵菜单、功能不可达、命名漂移三类各判一条）；逐个**点击**菜单后断言 hash 落地 + 顶栏标题 + 页面标题 + 结构块渲染 + 无 5xx/console error |
| `micro-app-matrix-probe.mjs` | **9 个微应用是否都被 shell 真的挂载出来**（31 项断言） | 逐个域断言 `.subapp-error`（MicroAppLoadError 面板）为 0 + 挂载点内容非空 + 无 5xx；注册/前缀/菜单入口三张表全部从 `bone-shell/src/App.tsx` 与 IAM 菜单解析（不写死端口表）。**停掉任一 dev server 即转红并精确指名**（变异实测：kill :3012 → 2 项红、exit 1） |
| `claim-toast-hygiene-probe.mjs` | **理赔错误提示卫生**：失败能不能自助定位 + 会不会成倍刷屏（10 项断言） | ①未指定案件时**不发起**理算查询（`claimId` 不再回落占位 `'1'`）且保持「尚未理算」空态；②指定真实案件仍正常查询（防误伤）；③**注入 404 + `SC_CLAIM_NOT_FOUND`** 断言 toast 显示语言包译文「案件不存在或不可见」而非笼统「待办加载失败」；④同一失败**只出现 1 条** toast（antd message `key` 去重）。注意：antd toast 默认 3s 消失，**必须轮询采样**，固定等待会整段错过而误报「没报错」。变异实测：恢复占位 `'1'` → 抓到 2 次 `/claims/1/adjust` 即红；恢复硬编码 catch → 复现原症状「待办加载失败」×2 且无原因 |

## 运行

```bash
NODE=$(command -v node)   # 实测版本随环境变化，勿写死路径（原写死 22.22.2-3 已失效）
export NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules   # playwright 在此
$NODE scripts/e2e/ui/probe-routes.mjs > /tmp/ui.json
python3 -c "import json;d=json.load(open('/tmp/ui.json'));print(len(d['results']),'项')"
```

## 写新探针前必读（都是本轮踩过的坑）

0. **样板走共享 client**：登录凭证、CHROME、UI BASE、playwright 加载等重复样板统一从
   `scripts/e2e/e2e_lib/client.cjs` 取——本目录探针全为 `.mjs`，一律
   `import { ... } from '../e2e_lib/client.cjs'`：

   ```js
   import { loadPlaywright, CHROME, UI_BASE as BASE, USER, PWD } from '../e2e_lib/client.cjs';
   const { chromium } = loadPlaywright();
   ```

   `loadPlaywright()` 先常规解析 `playwright`（命中 NODE_PATH），失败自动退回 workspace
   绝对路径——`NODE_PATH` 导出不再是硬前提。带 token 的 HTTP 请求优先用 client 的
   `login()/api()`，断言收集可用 `makeChecker()`；场景专属逻辑（本地 `report`/自定义汇总
   格式等）保留在探针内。
   **浏览器登录走 `e2e_lib/ui.cjs` 的 `uiLogin(page)`（2026-10-28 单族化收敛），禁止在探针内
   重抄登录选择器**；截图/就绪判定同理用 `shot()` / `waitAppReady()`。
   单族化后新增 UI 探针**只放本目录**、只用 `.mjs`（根目录不再新增 `ui-*` 探针）。
1. **路由清单唯一真源是运行时侧边栏**（`GET /api/v1/iam/menus/tree` 或读 DOM 菜单项）。
   静态 grep `path:` 拿不全 —— 9 个应用里只有 masterdata 用 `path:` 风格。
2. **iam 是两级菜单**：点子项前先展开父 SubMenu（判断 `aria-expanded` 幂等），
   否则子项不在 DOM（候选=0），表现为"点击失效、hash 不变"。
3. **按钮名不统一**：新增账号 / 新增角色 / 创建规则 / 新建连接器 / 新建流程 / 新建实体 /
   创建模型 / 导入配置 / 新建根部门 …… 探针里不要统一用"新增"。
4. **弹窗两种形态**：iam 的账号/角色/租户/部门是 `Drawer`（`.ant-drawer-content`），
   其余是 `Modal`。只认 `.ant-modal-content` 会把正常 Drawer 判成"弹窗未打开"。
5. **antd 菜单必须真实鼠标点击**（`locator.click()`），`li.click()` 对 Menu 无效。
6. **`useForm is not connected` 告警是探针时序问题**：在点开弹窗的瞬间读取，form 尚未挂载，
   产品正常，不要据此改代码。
7. 首次路由给 2.5~3.2s（Vite dev 按需编译）。

## 环境故障速查

| 现象 | 真因 | 处置 |
|---|---|---|
| 页面全 000，但日志写 ready | `ps` 被沙箱禁（`operation not permitted`） | 用 `lsof -nP -iTCP:<port> -sTCP:LISTEN`；端口真在监听就是活的 |
| dev server 日志出现 `SAFE_DELETE_BULK_CONFIRM_REQUIRED`（count>50, `deps_temp_*`） | Vite 预优化后清临时目录被 WorkBuddy safe-delete 拦下，**进程直接退出** | 把 `apps/*/node_modules/.vite/deps_temp_*` **移入回收站**（`mv`），再逐个重启 |
| dev server 跑着跑着消失 | `nohup & disown` 起的进程在工具调用结束后被回收 | 用 `run_in_background=true` 的 Bash 任务常驻 |
| 服务 503 `GW_UPSTREAM_UNAVAILABLE` | **网关熔断器**，不是服务挂 | 直连该服务端口复验（`curl --noproxy '*' http://127.0.0.1:<port>/actuator/health`） |
| 登录 200 但每个页面都 503 | 该服务进程**真的死了**（`lsof -nP -iTCP:<port> -sTCP:LISTEN` 无输出） | 重起并以后台任务常驻；`shell` 的 `menus/current` 失败会静默回退 `STATIC_MENU`，**菜单可见 ≠ 服务存活** |

## `probe-commerce.mjs` 专节（多渠道交易域）

只需 5 个进程：`gateway 8888 / iam 8081 / blueprint 8082 / shell 3000 / commerce-app 3012`
（**不用拉起全部 19 个服务**——上一轮「内存扛不住」的结论因此被推翻）。

```bash
NODE=$(command -v node)   # 实测版本随环境变化，勿写死路径（原写死 22.22.2-3 已失效）
export NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules
$NODE scripts/e2e/ui/probe-commerce.mjs
```

写它时新踩的坑（都会在别处复现）：

1. **判据要按页面形态分流**：支付管理是「发起/查询」表单页，`PaymentController` 根本
   没有列表端点。一律按 `rows>0` 判会**恒红**，改判 `input.ant-input >= 2`。
2. **别复用列表第一行的数据做写操作**：订单列表第一行往往已经 `PAID`，
   拿去「发起支付」会被 `BP_ORDER_STATUS_CONFLICT` 正确拒绝——看起来像产品缺陷，
   实为探针选数据的问题。要写就先 `POST /api/v1/channel-orders/pull` 现造一笔 `CREATED` 单。
3. **`paymentId` 只能从 `initiate` 响应体取**：mock 支付链接形如
   `https://mock-pay.local/pay?order=<orderId>&token=xxx`，里面没有 `paymentId`。
4. **`Input.TextArea` 没有 `<input>`**：`收货地址` 这类长文本字段要退化到 `textarea`，
   否则 `locator('input').fill()` 直接 30s 超时且报错信息不含字段名。
5. **提交按钮定位**：只用文案会被 hidden 弹窗残留坑，只用 `[type=submit]` 会被 loading 态坑。
   正确写法：`.ant-modal-content:visible button` + 文案，失败再退化到 `type=submit`。
6. **每个分节用 try/catch 包住并记 FAIL**：否则一个 `fill` 超时会让后面所有功能点失去覆盖。
