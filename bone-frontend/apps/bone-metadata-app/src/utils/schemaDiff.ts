/**
 * 发布 schema diff（PRD §5.4 规则 32：`+` / `✎` / `-` 三色）。
 *
 * 为什么是纯函数：后端 PublishPreview 的 `physical.statements` 已是「将要执行的 DDL」，
 * 从中解析新增/删除列即可得到与发布面板一致的 diff —— 不需要后端另存历史快照
 * （PRD G4：MVP 无版本快照，快照回滚 v1.1）。前端只做「语句 → 变更集」的确定性映射，
 * 保证预览、字段清单徽标、L1/L2 分级三处口径一致。
 *
 * L1/L2 分级（对齐 PRD §0.3 G6「MVP 最小集：L1/L2 影响提示」）：
 *  - L1 = 新增（ADD COLUMN / CREATE TABLE 整表新建）：向前兼容，低风险
 *  - L2 = 删除（DROP COLUMN）：破坏性变更，需二次确认
 *  - ✎（修改）当前不可达：后端发布保护禁止类型变更（见 PhysicalStructurePlan inspect 语义），
 *    一旦放开，在此扩展解析即可。
 */

export interface SchemaDiff {
  /** 将新增的列名（CREATE TABLE = 全部字段列；否则从 ADD COLUMN 提取） */
  addedColumns: string[];
  /** 将被删除的列名（DROP COLUMN 提取） */
  droppedColumns: string[];
  /** L1 影响数（新增，向前兼容） */
  l1Count: number;
  /** L2 影响数（删除，破坏性） */
  l2Count: number;
}

export const NO_DIFF: SchemaDiff = {
  addedColumns: [],
  droppedColumns: [],
  l1Count: 0,
  l2Count: 0,
};

/** DDL 语句 → 变更集（确定性解析，大小写不敏感地提取反引号列名） */
export function parseSchemaDiff(
  physical: { createTable: boolean; statements: string[] } | null | undefined,
  fieldCodes: string[],
): SchemaDiff {
  if (!physical || !Array.isArray(physical.statements)) return NO_DIFF;

  if (physical.createTable) {
    // 整表新建：所有业务字段列均为「新增」（L1）
    return {
      addedColumns: [...fieldCodes],
      droppedColumns: [],
      l1Count: fieldCodes.length,
      l2Count: 0,
    };
  }

  const addedColumns: string[] = [];
  const droppedColumns: string[] = [];
  for (const stmt of physical.statements) {
    const upper = stmt.toUpperCase();
    if (upper.includes('ADD COLUMN')) {
      const col = extractBacktickColumn(stmt);
      if (col) addedColumns.push(col);
    } else if (upper.includes('DROP COLUMN')) {
      const col = extractBacktickColumn(stmt);
      if (col) droppedColumns.push(col);
    }
  }
  return {
    addedColumns,
    droppedColumns,
    l1Count: addedColumns.length,
    l2Count: droppedColumns.length,
  };
}

/** 从 `ALTER TABLE `x` ADD COLUMN `col` ...` 提取第一个反引号包裹的列名 */
function extractBacktickColumn(stmt: string): string | null {
  // 语句里表名也是反引号包裹：`table` ADD COLUMN `col` —— 取「ADD/DROP COLUMN 之后」的第一个反引号段
  const markerIdx = stmt.toUpperCase().indexOf('COLUMN');
  if (markerIdx < 0) return null;
  const tail = stmt.slice(markerIdx + 'COLUMN'.length);
  const m = /`([^`]+)`/.exec(tail);
  return m ? m[1] : null;
}
