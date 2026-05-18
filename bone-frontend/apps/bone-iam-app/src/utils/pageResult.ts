import type { PageResult } from '../types';

/** 对齐 bone-core PageResult（records + total） */
export function unwrapPage<T>(page: PageResult<T> | null | undefined) {
  if (!page) {
    return { records: [] as T[], total: 0 };
  }
  const records = page.records ?? page.data ?? [];
  return { records, total: page.total ?? 0 };
}
