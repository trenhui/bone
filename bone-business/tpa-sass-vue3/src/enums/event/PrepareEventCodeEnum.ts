export enum PrepareEventCodeEnum {
  /** 表格行—查看 */
  TABLE_DETAIL = "table_detail",
  /** 表格行—编辑 */
  TABLE_EDIT = "table_edit",
  /** 表格行—删除 */
  TABLE_DELETE = "table_delete",
  /** 表格左表头—新增 */
  TABLE_CREATE = "table_create",
  /** 表格左表头—批量编辑 */
  TABLE_BATCH_EDIT = "table_batch_edit",
  /** 表格左表头—批量删除 */
  TABLE_BATCH_DELETE = "table_batch_delete",
  /** 表格左表头—复制 */
  TABLE_COPY = "table_copy",
}

export function isPrepareEvent(code: string): code is PrepareEventCodeEnum {
  return Object.values(PrepareEventCodeEnum).includes(
    code as PrepareEventCodeEnum
  );
}
