/**
 * 分页 `total` 归一（详见 `doc/architecture/Bone-API-规范.md` §5.3）。
 *
 * ## 为什么 `total` 是字符串而 ID 绝不能转
 *
 * 骨核 `PageResult.total` 声明为 `java.lang.Long`，而 `bone-metadata-sdk` 的
 * `MetadataAutoConfiguration.boneLongToStringCustomizer()` 全平台注册了
 * `serializerByType(Long.class, ToStringSerializer.instance)`，故**所有 `Long` 字段
 * 运行期都是 JSON 字符串**。同结构中的 `Integer` 字段（`page`/`size`/`pages`）不受影响。
 *
 * 该全局兜底的动机是**雪花 ID 精度保护**：18~19 位 long 超出 JS Number 安全上限
 * 2^53，以 JSON number 返回时前端拿到的 ID 末几位被静默截断，回传后端即 404。
 *
 * ⇒ **`Number()` 对雪花 ID 绝对禁止，但对 `total` 安全**：
 * `total` 是行数计数（远小于 2^53），不是标识符，两者不可混同处理。
 * 业务代码中 `String(id)` / `Number(id)` 的差异必须按「是否 ID」区分，不可全局套用。
 */

/**
 * 把分页响应里的 `total` 归一为 `number`。
 *
 * 未归一时字符串会穿透到 `Math.ceil(total / pageSize)` 等算术 —— JS 中
 * `'1055' / 10` 抛 `TypeError`（无隐式转换），表现为「删除末页最后一条后
 * 翻页回退」时整页崩溃。
 *
 * `Number.isFinite` 兜底：非数字字符串归一为 0 而非 `NaN`，避免污染下游比较。
 */
export function normalizeTotal(raw: unknown): number {
  if (raw === null || raw === undefined || raw === '') {
    return 0;
  }
  const n = Number(raw);
  return Number.isFinite(n) ? n : 0;
}
