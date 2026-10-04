import { normalizeTotal } from '@bone/shared-utils';
import type { PageResult } from '../types';

/**
 * `total` 必须归一为 number：运行期它是 **string**（后端 `PageResult.total` 为
 * `java.lang.Long`，被骨核全局 `Long`→String 序列化器接管）。
 * 详见 `doc/architecture/Bone-API-规范.md` §5.3。
 *
 * 归一点选在这里的原因：调用方普遍写 `Math.ceil(newTotal / pageSize)`
 *（删除末页最后一条后的翻页回退），而 JS 中`'1055' / 10` 抛 `TypeError`，
 * 字符串穿透会直接让页面崩溃。1 处归一即覆盖全部 6 个 IAM 列表页。
 *
 * 注意：此处`Number()` 安全，因 `total` 是行数计数而非标识符；
 * 雪花 ID 仍须禁止 `Number()`（见 shared-utils/paging.ts 的说明）。
 */
export { normalizeTotal };

/** 对齐 bone-core PageResult（records + total） */
export function unwrapPage<T>(
  page: PageResult<T> | null | undefined,
): { records: T[]; total: number } {
  if (!page) {
    return { records: [] as T[], total: 0 };
  }
  // 权威字段 records 放首位。原先把 list（后端 @Deprecated getter 的产物）放在
  // ?? 链首，是反的：list 一旦被 @JsonIgnore 抹掉，每次读取都要先撞一次 undefined
  // 才回落到 records。语义上等价，但把权威字段排在前更清晰，也不给废弃键任何优先级。
  const compat = page as unknown as { list?: T[]; data?: T };
  const records: T[] = (page.records ?? compat.list ?? compat.data ?? []) as T[];
  return { records, total: normalizeTotal(page.total) };
}
