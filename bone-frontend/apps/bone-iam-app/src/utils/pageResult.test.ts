import { describe, expect, it } from 'vitest';
import { unwrapPage } from './pageResult';
import type { PageResult } from '../types';

/**
 * `total` 的运行期类型是 **string**，不是 number。
 *
 * 根因：`PageResult.total` 在后端是 `java.lang.Long`，而
 * `MetadataAutoConfiguration.boneLongToStringCustomizer()` 全平台注册了
 * `serializerByType(Long.class, ToStringSerializer.instance)`（为保护雪花 ID 精度）。
 * 同结构中的 `page`/`size`/`pages` 是 `Integer`，不受影响。
 *
 * 实测（2026-10-03，`GET /api/v1/iam/audit/logs`）：
 * `total: '1055'`（string） / `page: 1`（number） / `pages: 528`（number）
 *
 * 修复前 `unwrapPage` 直接 `return page.total ?? 0`，字符串会原样穿透到调用方的
 * `Math.ceil(newTotal / pageSize)` —— JS 中 `'1055' / 10` 抛 `TypeError`，
 * 表现为「删除末页最后一条后翻页回退」时页面崩溃。
 */
describe('unwrapPage · total 归一化', () => {
  it('total 为字符串时必须归一为 number（防TypeError）', () => {
    const page = { records: [{ id: 1 }], total: '1055' } as unknown as PageResult<{ id: number }>;
    const { total } = unwrapPage(page);

    expect(total).toBe(1055);
    expect(typeof total).toBe('number');
  });

  it('归一后可安全参与除法/ceil（翻页回退的真实调用形态）', () => {
    const page = { records: [], total: '1055' } as unknown as PageResult<{ id: number }>;
    const { total } = unwrapPage(page);

    // 6 个 IAM 页面都是这个写法：Math.ceil(newTotal / pageSize)
    expect(() => Math.ceil(total / 10)).not.toThrow();
    expect(Math.ceil(total / 10)).toBe(106);
  });

  it('total 为 number 时保持原值（不回归）', () => {
    const page = { records: [], total: 42 } as unknown as PageResult<{ id: number }>;
    expect(unwrapPage(page).total).toBe(42);
  });

  it('total 为空/undefined/0 时归一为 0', () => {
    expect(unwrapPage({ records: [], total: undefined } as unknown as PageResult).total).toBe(0);
    expect(unwrapPage(null).total).toBe(0);
    expect(unwrapPage({ records: [], total: '0' } as unknown as PageResult).total).toBe(0);
  });

  it('非数字字符串归一为 0（不产生 NaN）', () => {
    const { total } = unwrapPage({ records: [], total: 'abc' } as unknown as PageResult);
    expect(total).toBe(0);
    expect(Number.isNaN(total)).toBe(false);
  });

  it('records 归一不受total 归一影响', () => {
    const page = { records: [{ id: 7 }], total: '9' } as unknown as PageResult<{ id: number }>;
    const { records, total } = unwrapPage(page);
    expect(records).toEqual([{ id: 7 }]);
    expect(total).toBe(9);
  });
});

/**
 * `records` 是权威当前页字段（Bone-API-规范 §3.3/§5.3）；`list` 是后端
 * `PageResult.getList()` 这个 @Deprecated 兼容 getter 的产物，`@JsonIgnore`
 * 收敛后会从响应里消失。这组用例钉住两件事：
 *
 * 1. 只给 `records`（收敛后的真实响应形态）必须能取到数据；
 * 2. 过渡期响应同时含两键时，`records` 优先于 `list`——
 *    权威字段不能排在废弃字段之后。
 */
describe('unwrapPage · records 权威字段', () => {
  it('只给 records（list 收敛后的响应）能正常取值', () => {
    const page = { records: [{ id: 1 }, { id: 2 }], total: 2 } as unknown as PageResult<{ id: number }>;
    expect(unwrapPage(page).records).toEqual([{ id: 1 }, { id: 2 }]);
  });

  it('两键并存时 records 优先于 list（废弃键不得排在权威键之前）', () => {
    const page = {
      records: [{ id: 1 }],
      list: [{ id: 999 }],
      total: 1,
    } as unknown as PageResult<{ id: number }>;
    // 取到 id:1 而非 id:999 —— 若实现写成 `page.list ?? page.records`，
    // 后端收敛 list 后每次都要撞一次 undefined 才回落，虽结果仍对但优先级是反的。
    expect(unwrapPage(page).records).toEqual([{ id: 1 }]);
  });

  it('过渡期只有 list 时仍兼容读取（@JsonIgnore 落地前的存量数据）', () => {
    const page = { list: [{ id: 5 }], total: 1 } as unknown as PageResult<{ id: number }>;
    expect(unwrapPage(page).records).toEqual([{ id: 5 }]);
  });

  it('空响应返回空数组而非 undefined', () => {
    expect(unwrapPage({ total: 0 } as unknown as PageResult).records).toEqual([]);
  });
});