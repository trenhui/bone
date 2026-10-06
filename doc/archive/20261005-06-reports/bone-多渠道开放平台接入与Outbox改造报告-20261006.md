# 多渠道开放平台真实接入 · 买家映射 · 库存广播 Outbox —— 设计与联调报告（2026-10-06）

> 范围：接真实渠道开放平台（重写 12 个渠道扩展实现）、「渠道买家 ↔ 内部客户」映射表、库存广播改 Outbox 异步。
> 验证状态：**编译 ✅ / 275 单测 ✅ / 31 条静态门禁 ✅ / 前端 tsc+eslint ✅**；E2E 与 UI 巡检 ⏸ **未跑（本机 MySQL 未运行，见第六节）**。

---

## 一、接真实渠道开放平台

### 1.1 分层：扩展实现只答业务语义，协议差异收在一处

原有 12 个扩展（4 渠道 × 订单/商品/物流）是「本地模拟」：打印日志 + 造一个确定性 ID。真实开放平台四家协议各不相同，若让每个扩展自己处理签名与网关，会出现 12 套签名实现——**签名写错是最难定位的错误**（渠道侧只回一句 `sign error`，本地永远测不出来，因为本地没有渠道校验）。

新增协议层 `bone-blueprint/.../infrastructure/channel/openapi/`（19 个类）：

| 构件 | 职责 |
|---|---|
| `ChannelApiSpec` + 4 个 spec | 「这个平台长什么样」：网关地址、签名算法、令牌参数名/Header、凭证槽位、公共参数、响应解包 |
| `AbstractChannelApiSpec` | 骨架：找错误体（根层或嵌套）→ 顶层 `code` → 取业务体（候选键 → 兜底扫 `*_response`/`data`） |
| `ChannelOpenApiClient` | 「怎么调」：凭证 → 公共参数 → 签名 → 超时 → 重试 → 解析 → 日志 |
| `ChannelJson` | `str/longOf/intOf/decimal/list` 点号路径导航，避免扩展里写 `JsonNode.get().get()` |
| `ChannelCredentialProvider` | 凭证来源（静态配置 + 运行时刷新），**缺失即失败关闭** |
| `JdkChannelHttpExecutor` | JDK `HttpClient` 传输（可替换为 Mock/WebClient） |

12 个扩展改写后只做三件事：调哪个接口、带什么业务参数、响应怎么映射成领域对象。**加第五个渠道时客户端零改动**。

### 1.2 四家的协议差异（都已在代码中处理）

| 渠道 | 网关 | 签名 | 密钥位置 | 令牌 | 错误位置 | 金额单位 |
|---|---|---|---|---|---|---|
| 淘宝 TOP | `eco.taobao.com/router/rest` | MD5 大写 | 前后各一份 | `access_token` 参数 | 根 `error_response` | 元 |
| 京东宙斯 | `router.jd.com/api` | MD5 大写 | **只拼尾部** | `token` 参数 | `jd_response_content` | **分** |
| 抖音电商 | `openapi-fxg.jinritemai.com` | **HMAC-SHA256** | 不拼（HMAC 承载） | **`Access-Token` Header，不参与签名** | `code != 0` | **分** |
| 拼多多 | `gw-api.pinduoduo.com/api/router` | MD5 大写 | 前后各一份 | `access_token` 参数 | **嵌在 `xxx_response.error_response` 内** | **分** |

两个必须显式处理的静默错误：

1. **金额单位是「分」**。京东/抖音/拼多多若直接塞进订单草稿，金额放大 100 倍且**不报错**。统一走 `toYuan()`。
2. **京东响应是二次 JSON**（`jd_response_content.result` 本身是 JSON 字符串），由 spec 的 `resultWrappedAsJson` 解开。

### 1.3 两条不做的事（比做了更重要）

- **MOCK 通道不伪造渠道数据**：`transport=MOCK` 时渠道侧无业务体（`data == null`），扩展回落调用方传入的上下文。这是本地/CI 唯一诚实的表达——否则会得到「自动化全绿，但真实通道从未被验证过」。
- **凭证缺失不静默回落 MOCK**：切到 `HTTP` 而凭证没配 → 立即抛 `BP_CHANNEL_CREDENTIAL_MISSING`。生产上「以为在同步真实渠道、实际一直在跑模拟」是最危险的一类静默。

### 1.4 ⚠️ 待核对接口名清单（接入前必须按平台当期文档核对）

平台接口多次改版，以下 4 处**没有猜接口名**，而是在常量 javadoc 标注 `【待核对】`：

| 位置 | 当前占位 | 说明 |
|---|---|---|
| 京东商品下架 / 库存同步 | `ware.write.update` / `ware.stock.update` | 宙斯商品侧接口按年改版 |
| 京东发货回传 | `jos.order.status.update` | 京东以「配送单/运单」或「订单状态」表达已发货 |
| 抖音发货回传 | `order.ship` | 抖音电商发货接口名需核对 |
| 淘宝轨迹查询 | `taobao.logistics.trace.search` | TOP 未对卖家开放通用轨迹查询 |

**为什么不猜**：京东对不存在的接口返回 `400 / 无权限或接口不存在`，与「商品类目错误」的报文几乎一样。猜错时线上表现是「所有上架都失败但看不出原因」——比明确标注更难排查。

**已确认的真实接口名**（可直接用）：`taobao.trade.orders.get`、`taobao.logistics.trace.publish`、`taobao.item.add/update`、`taobao.quantity.update`、`ware.write.add`、`order.searchList`（抖音/拼多多）、`product.addV2/offline/updateStock`、`goods.add/update_status/update_stock`、`order.logistics.add`。

> 另有一处**刻意不调渠道**：拼多多商家侧没有独立的「订单状态回传」接口，「已发货」由添加物流驱动（该调用在 `PddLogisticsExtension#pushShipment`）。`PddOrderExtension#ackOrder` 只做幂等确认并说明真实回传路径，不编造接口。

---

## 二、「渠道买家 ↔ 内部客户」映射

### 2.1 替换掉的做法与原因

原实现：`customerId = buyerNick.hashCode() & 0x7fffffff + 1000000`。三个不可接受的问题：

1. **昵称可改** → 改名即换客户，同一个人的订单被拆散，客户维度统计/会员权益/售后全部失真；
2. 哈希值**不是真实客户ID**，无法与会员/积分/售后/对账 join；
3. 不同租户可能撞值。

映射键改为**渠道买家账号ID**（淘宝 `buyer_user_id` / 京东 `buyererdno` / 抖音 `buyer_second_id` / 拼多多 `user_id`）——昵称是展示，ID 才是身份。

### 2.2 聚合与状态

新聚合 `ChannelBuyer`（表 `bp_channel_buyer`，唯一键 `tenant + channel + buyerId`）：

- `bindingSource`：`MANUAL`（人工绑定）/ `AUTO_SHADOW`（拉单自动建的影子）。**分开而非一个 bool**：影子只是「见过这个买家」，把它和人工绑定混在一起，客服与对账无法区分「真的对上了」还是「系统还没绑」。
- 影子态 `customerId = 0`（0 不是合法客户ID，列 `NOT NULL DEFAULT 0`）。
- 行为：`observe` / `bindTo` / `rebind` / `unbind` / `recordOrder` / `refreshNick`。

### 2.3 关键设计：R9「一事务一聚合」下如何写映射

拉单事务（`ChannelOrderApplicationService#pullAndCreate`）只允许碰 `Order` 一个聚合，**写映射必须出事务**：

```
拉单事务：读映射（只读不违反边界）→ 落单 → publish(ChannelBuyerObservedEvent)
                                            ↓ AFTER_COMMIT
                          ChannelBuyerObservedEventHandler
                            （TenantContextRunner.runAs + REQUIRES_NEW）
                            → 建影子映射 / 累计笔数
```

- 事件必须实现 `DomainEvent`（否则 `publish` 编译不过）；
- AFTER_COMMIT 线程**没有 HTTP 上下文**，`TenantContext` 为 null，而 SDK 写路径按 ADR-0029 失败关闭 ⇒ 必须 `TenantContextRunner.runAs`，否则映射永不建立且失败被 catch 吞掉；
- 租户判脏用 `< 0`（不是 `<= 0`）：`tenantId=0` 是合法平台租户。

### 2.4 影子客户为什么不拒绝建单

未绑定的渠道买家，订单落 `customerId = 0`（未知客户）**并继续建单**。拒绝会让渠道侧表现为「店铺没收到订单」，是比「客户未识别」严重得多的故障。影子记录随后出现在「待绑定清单」里（`/shadow-candidates`，按最近拉单时间倒序），由运营或规则补绑。

### 2.5 绑定的三种动作（刻意不合并）

| 端点 | 语义 | 为什么分开 |
|---|---|---|
| `bind` | 仅未绑定时可绑，已绑返回 409 | 避免日常绑定顺手覆盖历史归属 |
| `rebind` | 显式改绑，**必须填原因** | 改绑要留痕（售后申诉/对账差异） |
| `unbind` | 退回影子态 | 绑错了 / 客户已合并注销 |

---

## 三、库存广播改 Outbox 异步

### 3.1 换掉的做法与三个致命问题

原实现：`ChannelProductApplicationService#syncInventoryToAllChannels` 在**业务事务内串行**调 4 个渠道。

1. **事务被网络 IO 拖长**：渠道超时 + 重试 → 库存事务持有行锁数十秒，连接池被同步 IO 占满；
2. **两侧不一致无法区分**：渠道 A 已改成功、B 超时 → 事务回滚但 A 已改，没有任何机制能对齐回来；
3. **失败无处可查**：只留一行日志，渠道库存长期停在旧值，直到其他渠道超卖才被发现。

### 3.2 现在的链路

```
库存变更（receive/deduct/reserve/release）
   └─ 同事务 appendStockBroadcast（@Transactional(MANDATORY)，无事务直接抛）
        ↓ 原子提交
bp_channel_broadcast_task (PENDING)
        ↓ ChannelBroadcastRelayJob（adapter/schedule，fixedDelay 5s）
     CAS 抢占 → 合并同商品同渠道 → 事务外调渠道 → 标记终态
        ↓                              ↓
   SENT（+ 回写渠道商品库存快照）   退避重试 → 耗尽转 FAILED（死信）→ 人工重试
```

| 设计点 | 做法 | 为什么 |
|---|---|---|
| 写侧传播 | `Propagation.MANDATORY` | 用默认 `REQUIRED` 时，无事务调用会**悄悄新起事务** ⇒ 库存提交了但广播没入队，且无任何报错 |
| 抢占 | `updateByCriteria` CAS（`WHERE status='PENDING'`） | 禁 `FOR UPDATE`（HC-0031）；多实例各抢互不重叠行集，零死锁 |
| 网络 IO | 在 DB 事务外 | 不占连接池 |
| 终态标记 | `TransactionTemplate(REQUIRES_NEW)` | 内部方法互调时 Spring AOP 不生效，注解会被静默忽略 |
| 连续变更 | 同 `(product, channel)` 只投 `createdAt` 最大的，其余标 SENT + `mergedIntoId` | 促销时 100→80→60 逐条同步会让渠道被打 3 次，而渠道只需最终值 |
| 退避 | `base * 2^(n-1)`，封顶 5 分钟；查询条件 `nextRetryAt <= now` | **缺了 `nextRetryAt <= now` 过滤，退避形同虚设**，限流时会被打成重试风暴 |
| 人工重试 | `requeueFailed` 带原状态 CAS | 不带 CAS 时，连点两次或与中继并发会把已判死的任务复活并清零重试计数 ⇒ 死信永动机 |
| 卡死自愈 | `reconcileStuck`（`updatedAt` 超 120s 回退 PENDING） | 进程崩溃遗留的 PROCESSING 不会自己恢复 |

### 3.3 两个容易做错的口径

- **传可售量 `availableQty` 而非实物总量**：预留已从 available 扣减，传总量会让预留中的订单继续在渠道可买 → 超卖。
- **`confirm`（确认出库）不广播**：它只减预留量，可售量未变；`release`（释放预留）要广播，否则渠道侧仍显示售罄。

### 3.4 端点语义变更（调用方需知）

`POST /api/v1/channel-products/sync-inventory` 现在**只入队**并立即推进一轮中继，返回「已入队的渠道商品」而非「渠道已同步成功」。返回条数仍等于 ONLINE 渠道数（E2E 的 `== 4` 断言不受影响）。

新增 `/api/v1/channel-broadcasts`（page / retry / relay-now），授权 `commerce:broadcast:read|write`。

---

## 四、数据库与权限

### 4.1 迁移与建库真源（三处必须同步）

| 文件 | 内容 |
|---|---|
| `scripts/migration/0022_channel_buyer_mapping.sql` | `bp_channel_buyer` + `t_order.channel_buyer_id` |
| `scripts/migration/0023_channel_broadcast_outbox.sql` | `bp_channel_broadcast_task` |
| `bone-init.sql` | 建库真源：补进 7 张多渠道表（**上一轮 0021 的 5 张表原本只进了 migration，是缺口**）+ `t_order` 三个多渠道列 + 4 条渠道种子 |
| `doc/architecture/数据库开发规范.md §2` | 表清单 88 → 95（`check-ddl-doc-sync` 在补表后立刻红，逼着同步） |

同时修掉 `bone-init.sql` 第 324 行的**上一轮遗留 SQL 语法错误**：第 107 行权限码后误加 `;` 提前终止 `INSERT INTO iam_permission`，其后所有行成了裸表达式——该文件在重新建库时会在此处直接失败。

### 4.2 权限码与错误码（四处真源已同步）

- 新增 4 个权限码（`108~111`）：`commerce:channel-buyer:read|write`、`commerce:broadcast:read|write` → `bone-init.sql` 种子 + 前端 `bonePermissionCodes.ts`。
- 6 个新错误码（`BP_CHANNEL_CREDENTIAL_MISSING` / `OPENAPI_FAILED` / `OPENAPI_REJECTED` / `BUYER_NOT_FOUND` / `BUYER_ALREADY_BOUND` / `BROADCAST_NOT_FOUND`）四处已齐：常量 → `DEFAULT_HTTP_STATUS` → 错误码台账 → zh/en 语言包（`check-i18n-sync` 绿）。

---

## 五、前端

| 页面 | 路由 | 要点 |
|---|---|---|
| 渠道买家映射 | `/channel-buyers` | 默认「待绑定」筛选；绑定/改绑/解绑三个弹窗；改绑强制填原因；ID 全部字符串 |
| 库存广播任务 | `/broadcast-tasks` | 默认「仅失败」筛选；失败原因直接展示渠道原始 message；「立即推送一轮」；被合并任务显式标注 |

- 类型与状态元数据加在 `types/index.ts` / `pages/channelConstants.ts`；API 加在 `services/api.ts`。
- shell 侧边栏加两项菜单（`TeamOutlined` / `ThunderboltOutlined`）。
- 遵循既有硬约束：日期用 `formatDate()`（不碰 `toLocaleString`）、ID 一律 string、`Input` 收雪花 ID（不用 `InputNumber`）。

---

## 六、验证证据与未完成项

### 6.1 已验证（实测输出）

| 项 | 结果 |
|---|---|
| `mvn -o -pl bone-blueprint clean compile` | 238 文件 **BUILD SUCCESS** |
| `mvn -o -pl bone-blueprint test` | **275 tests, 0 failures, 0 errors** |
| 31 条静态门禁（`scripts/check-*.py` + `ci/` + `frontend/`） | **全部 EXIT=0** |
| `tsc --noEmit`（commerce + shell） | 通过 |
| `eslint`（新增前端文件） | **0 error**（31 warning 为既有 `explicit-module-boundary-types`） |

> 过程中修掉的真实缺陷：枚举常量调用实例方法（`ChannelSignatureAlgorithm`）、`ChannelOpenApiProperties` 漏 `@Component` 导致 Context 加载失败、`ChannelOpenApiProperties` 键未定义触发 `ConfigKeysContractTest`、SDK `Criteria` 无 `le` 方法、`findPage` 用 `eq(1L)` 判「已绑定」的逻辑错误、`ChannelBuyer` 缺纯单测触发聚合覆盖门禁、`bone-init.sql` 的 `;` 断句错误。
>
> 另有两个 `NoClassDefFound`（`OrderAssemblerTest`/`ProblemDetailContractTest`）**不是代码缺陷**：`javap` 显示测试 class 的字段类型是 `private final OrderAssembler assembler;`（**丢了包名**，javac 不会这样编译），是 IDEA 的 ECJ 编译器覆写了 maven 的 `target/test-classes`。移走坏 class 后重跑即绿。

### 6.2 未完成：E2E 与 UI 巡检（本机环境阻塞）

本机 **MySQL 未运行**（`lsof -iTCP:3306` 空、`docker ps` 空、无 `mysql` 客户端），因此无法执行迁移、无法起服务，E2E（`scripts/e2e/multichannel.py` 47 项）与 UI 探针（`scripts/e2e/ui/probe-commerce.cjs` 30 项）**本轮未跑**。

环境就绪后的验证清单：

1. 执行 `mysql --default-character-set=utf8mb4 -uroot -p bone < scripts/migration/0022_channel_buyer_mapping.sql` 与 `0023_channel_broadcast_outbox.sql`（校验段应回 `OK: ...`）；
2. 拉单带 `buyerId` → 查 `/api/v1/channel-buyers?bound=false` 应出现影子映射 → `bind` → 再拉单 → 订单 `customer_id` 应为真实客户 ID；
3. `POST /api/v1/channel-products/sync-inventory` 返回 4 条入队 → `POST /api/v1/channel-broadcasts/relay-now` → 任务转 SENT、渠道商品 `listing_stock` 回写；
4. 失败路径：临时把某渠道凭证清空并切 `HTTP` → 任务应退避重试后转 FAILED（死信）→ 人工 `retry` 可恢复；
5. UI：`/channel-buyers` 与 `/broadcast-tasks` 两页渲染、绑定弹窗校验、重试按钮；
6. `scripts/e2e/multichannel.py` 与 `probe-commerce.cjs` 全量回归。

> 建议同时把 `multichannel.py` 扩展到覆盖新链路（买家映射绑定流程 + 广播任务投递/死信/重试），否则这三项新能力仍缺自动化守护。
