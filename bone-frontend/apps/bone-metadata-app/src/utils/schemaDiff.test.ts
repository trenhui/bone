import { describe, expect, it } from 'vitest';
import { NO_DIFF, parseSchemaDiff } from './schemaDiff';

describe('parseSchemaDiff', () => {
  it('无 physical 时返回 NO_DIFF', () => {
    expect(parseSchemaDiff(null, ['a', 'b'])).toEqual(NO_DIFF);
    expect(parseSchemaDiff(undefined, ['a', 'b'])).toEqual(NO_DIFF);
  });

  it('CREATE TABLE 整表新建：所有业务字段列均为 L1 新增', () => {
    const diff = parseSchemaDiff(
      {
        createTable: true,
        statements: ['CREATE TABLE IF NOT EXISTS `meta_bp_order` (`id` BIGINT NOT NULL, `qty` INT)'],
      },
      ['qty', 'status'],
    );
    expect(diff.addedColumns).toEqual(['qty', 'status']);
    expect(diff.l1Count).toBe(2);
    expect(diff.l2Count).toBe(0);
    expect(diff.droppedColumns).toEqual([]);
  });

  it('ADD COLUMN：提取加列语句中的列名（表名同用反引号不误伤）', () => {
    const diff = parseSchemaDiff(
      {
        createTable: false,
        statements: [
          'ALTER TABLE `meta_order` ADD COLUMN `priority` INT NOT NULL DEFAULT 0',
          'ALTER TABLE `meta_order` ADD COLUMN `note` VARCHAR(255)',
        ],
      },
      ['priority', 'note', 'qty'],
    );
    expect(diff.addedColumns).toEqual(['priority', 'note']);
    expect(diff.l1Count).toBe(2);
    expect(diff.l2Count).toBe(0);
  });

  it('DROP COLUMN：提取为 L2 破坏性变更', () => {
    const diff = parseSchemaDiff(
      {
        createTable: false,
        statements: ['ALTER TABLE `meta_order` DROP COLUMN `legacy_flag`'],
      },
      ['qty'],
    );
    expect(diff.droppedColumns).toEqual(['legacy_flag']);
    expect(diff.l2Count).toBe(1);
    expect(diff.l1Count).toBe(0);
  });

  it('混合 ADD + DROP：L1/L2 分别计数', () => {
    const diff = parseSchemaDiff(
      {
        createTable: false,
        statements: [
          'ALTER TABLE `t` ADD COLUMN `a` INT',
          'ALTER TABLE `t` DROP COLUMN `b`',
          'ALTER TABLE `t` ADD COLUMN `c` INT',
        ],
      },
      ['a', 'c', 'x'],
    );
    expect(diff.addedColumns).toEqual(['a', 'c']);
    expect(diff.droppedColumns).toEqual(['b']);
    expect(diff.l1Count).toBe(2);
    expect(diff.l2Count).toBe(1);
  });

  it('语句为空（结构与模型一致）：无变更', () => {
    const diff = parseSchemaDiff({ createTable: false, statements: [] }, ['qty']);
    expect(diff.l1Count).toBe(0);
    expect(diff.l2Count).toBe(0);
    expect(diff.addedColumns).toEqual([]);
  });

  it('大小写不敏感（add column / drop column 小写）', () => {
    const diff = parseSchemaDiff(
      {
        createTable: false,
        statements: ['alter table `t` add column `lo` varchar(10)', 'alter table `t` drop column `hi`'],
      },
      ['lo'],
    );
    expect(diff.addedColumns).toEqual(['lo']);
    expect(diff.droppedColumns).toEqual(['hi']);
  });

  it('非 ADD/DROP 语句（如索引、注释）不产生变更', () => {
    const diff = parseSchemaDiff(
      {
        createTable: false,
        statements: ['CREATE INDEX `idx_x` ON `t` (`a`)', '-- 注释行'],
      },
      ['a'],
    );
    expect(diff.addedColumns).toEqual([]);
    expect(diff.droppedColumns).toEqual([]);
  });
});
