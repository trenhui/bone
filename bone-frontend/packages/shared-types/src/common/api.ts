/**
 * 统一 API 响应与分页类型（公共层）
 *
 * 对齐后端：
 * - `com.bone.core.model.ApiResponse`（注意包路径是 `model`，不是 `api`）
 * - `com.bone.core.model.PageResult`
 *
 * 字段形态以 `doc/architecture/Bone-API-规范.md` §3 为准；下列定义按**现状**书写
 * （保持与 8 个子应用既有调用兼容），已知偏差见各字段注释。
 */

/**
 * 统一 API 响应包装类型
 *
 * 后端实际还多两个字段（实测 `/api/v1/**` 响应顶层键为
 * `['code','data','error','message','success','timestamp']`）：
 * - `success: boolean`：等价于 `!error`，与 `code===200` 一致
 * - `timestamp: string`：ISO-8601 带时区（如 `2026-10-02T20:14:00.000Z`）
 * - `error: boolean`：与 `success` 互为取反
 *
 * 此处未声明是因为多数调用点只读 `code`/`data`/`message`，补齐属独立改动。
 */
export interface ApiResponse<T = unknown> {
  code: number;
  message: string;
  data: T;
}

/**
 * 统一分页结果类型
 *
 * ## 权威字段是 `records`/`page`/`size`，不是这里的 `list`/`pageNum`/`pageSize`
 *
 * 依据 `Bone-API-规范.md` §3.3：分页响应权威形态为
 * `records` / `total` / `page` / `size` / `pages` / `hasNext` / `hasPrevious` / `nextCursor`，
 * 且明文规定「`records` —— 当前页（禁止 `list`/`items`）」。
 *
 * 骨核 `PageResult` 另有 3 个 `@Deprecated` 兼容 getter（`getList`/`getPageNum`/`getPageSize`），
 * Jackson 默认不因 `@Deprecated` 忽略它们，故**响应中两组键会同时存在**。
 * 本类型 2026-10-03 起把 `records` 设为唯一必填字段，前端全部读取点已同步迁移
 * （`list` 收敛后消失，见 `Bone-API-规范.md` §5.3）。
 * 过渡期若需读旧键，用 `PageResultIamCompat` 而非给本类型加回 `list` ——
 * 否则废弃字段会重新变成「看起来是权威」的存在。
 *
 * ## 另一处已知偏差：`total` 运行期是字符串
 *
 * `PageResult.total` 后端为 `java.lang.Long`，而 `bone-metadata-sdk` 的
 * `MetadataAutoConfiguration.boneLongToStringCustomizer()` 全平台注册了
 * `serializerByType(Long.class, ToStringSerializer.instance)`（为保护雪花 ID 不被 JS Number
 * 截断），故**所有 `Long` 字段序列化为字符串**；同结构中 `Integer` 字段（`page`/`size`/`pages`）
 * 不受影响，仍是 number。
 *
 * 实测（2026-10-03，`GET /api/v1/iam/audit/logs`）：`total: '1055'`（str）/ `page: 1`（int）。
 * 架构裁定（2026-10-03）：**`total` 后端保持 `java.lang.Long`**，不动全局序列化器
 *（改它会削弱雪花 ID 精度保护）。因此本类型声明为 `string | number` 如实反映运行期。
 *
 * 消费方在算术/比较前必须 `Number()`：用 `normalizeTotal()`（`@bone/shared-utils`的 `paging.ts`）
 * 在各收敛点归一，切勿对雪花 ID 用 `Number()`（会静默截断，两者不可混同）。
 *
 * 此前声明为 `number` 属type lie，会让 `setTotal(res.data.total)` 这类透传静默通过编译；
 * 改为联合类型后，编译器会强制消费点显式归一（该改动曾暴露 9 处 TS2345，现已全部处理）。
 * 根因与实测证据见 `doc/architecture/Bone-API-规范.md` §5.3。
 */
export interface PageResult<T = unknown> {
  /**
   * 权威当前页字段（Bone-API-规范 §3.3/§5.3）。
   *
   * 后端 `PageResult` 的字段名就是 `records`（`@JsonAlias("list")` 只影响反序列化，
   * 序列化仍输出字段名本身），故这是**唯一应当读取**的键。
   */
  records: T[];
  total: string | number;
  pageNum: number;
  pageSize: number;
}

/**
 * `total` 已归一为 `number` 的分页结果 —— 各收敛点（`unwrapPage` / `normalizePage`）
 * 归一后的返回类型。
 *
 * 存在的意义：让「已归一」这一事实在类型上可见，调用方拿到它就能直接喂给
 * `useState<number>` 或参与算术，不必再写 `Number(...)`；而原始 `PageResult`
 * 因`total` 是联合类型，编译器会强制消费点显式处理。
 */
export interface NormalizedPageResult<T = unknown> {
  /** 与 `PageResult.records` 同名，保持「归一只改total、不改当前页字段名」的最小面。 */
  records: T[];
  total: number;
  pageNum: number;
  pageSize: number;
}

/**
 * @deprecated IAM 兼容别名。iam 旧读取点使用 records / data，请逐步迁移到 PageResult。
 * 保留 records / data 字段仅用于过渡期读取兼容。
 */
export interface PageResultIamCompat<T = unknown> {
  total: number;
  pageNum?: number;
  pageSize?: number;
  /** 权威字段：与 `PageResult.records` 一致 */
  records?: T[];
  /** @deprecated 旧前端字段（后端 `data` 键），兼容读取 */
  data?: T;
  /**
   * @deprecated 后端 `PageResult.getList()` 的产物，`@JsonIgnore` 收敛后消失（规范 §5.3）。
   * 仅在过渡期保留读取能力，新代码不得依赖。
   */
  list?: T[];
}

/**
 * 通用分页查询参数（**入参**用 `pageNum`/`pageSize`）
 *
 * 注意分页的**入参与出参是两套命名**（`Bone-API-规范.md` §5）：
 * - 入参：后端共存三族 —— `page`/`size`（iam、generator）、`pageNum`/`pageSize`
 *   （system、metadata、integration、masterdata、blueprint）、`cursor`/`limit`。
 *   本接口对应**第二族**，调用前须确认目标端点属哪一族，传错名会被 Spring 静默忽略。
 * - 出参：见上方 `PageResult`，权威字段是 `records`/`page`/`size`。
 *
 * 用前先核对该端点的分页参数名；`page`/`size` 族可直接用 `PageParam`（同文件未导出时内联 `{page,size}`）。
 */
export interface PageQuery {
  pageNum: number;
  pageSize: number;
}
