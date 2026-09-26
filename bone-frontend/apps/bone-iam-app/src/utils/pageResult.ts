import type { PageResult } from '../types';

/** 对齐 bone-core PageResult（records + total） */
export function unwrapPage<T>(
  page: PageResult<T> | null | undefined,
): { records: T[]; total: number } {
  if (!page) {
    return { records: [] as T[], total: 0 };
  }
  const records: T[] = (page.list ?? page.records ?? page.data ?? []) as T[];
  return { records, total: page.total ?? 0 };
}
