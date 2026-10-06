# 前端 UI 巡检探针

无头 Chrome（Playwright）驱动的三个探针，覆盖"点击每一个功能"。**先起全环境**：

```bash
bash scripts/dev/up-all-frontend-e2e.sh          # 9 个 Vite dev server
bash scripts/dev/up-all-services-e2e.sh          # 10 个 Spring Boot 服务
```

## 三个探针的分工

| 探针 | 作用 | 关键判据 |
|---|---|---|
| `probe-buttons.cjs` | 采集各页面**真实**菜单项与按钮名 | 静态 grep 拿不全路由/按钮名，一切以运行时为准 |
| `probe-routes.cjs` | 逐菜单**点击**导航 + 断言业务元素 | `tableRows>0→DATA` / `empty→EMPTY` / `cards→CARDS` / `inputs→FORM` / 否则 `THIN`；同时收集 4xx/5xx 与 console error |
| `probe-write.cjs` | 写入口深度交互 | 点"新增" → 断言弹窗开（**Modal 与 Drawer 都认**）→ 空提交看校验 → 取消能关 |
| `probe-commerce.cjs` | **多渠道交易域写操作闭环** | 开弹窗→填表→提交→断言 antd message；30 项断言，见下节 |

## 运行

```bash
NODE=/Users/renhui.trh/.workbuddy/binaries/node/versions/22.22.2-3/bin/node
export NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules   # playwright 在此
$NODE scripts/e2e/ui/probe-routes.cjs > /tmp/ui.json
python3 -c "import json;d=json.load(open('/tmp/ui.json'));print(len(d['results']),'项')"
```

## 写新探针前必读（都是本轮踩过的坑）

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

## `probe-commerce.cjs` 专节（多渠道交易域）

只需 5 个进程：`gateway 8888 / iam 8081 / blueprint 8082 / shell 3000 / commerce-app 3012`
（**不用拉起全部 19 个服务**——上一轮「内存扛不住」的结论因此被推翻）。

```bash
NODE=/Users/renhui.trh/.workbuddy/binaries/node/versions/22.22.2-3/bin/node
export NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules
$NODE scripts/e2e/ui/probe-commerce.cjs
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
