// @vitest-environment node
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { LIBRARY_MAX_PAGES, LIBRARY_PAGE_SIZE } from '../utils/libraryPaging';

const { listHospitals, listDiseases, listDrugs, listFeeCategories } = vi.hoisted(() => ({
  listHospitals: vi.fn(),
  listDiseases: vi.fn(),
  listDrugs: vi.fn(),
  listFeeCategories: vi.fn(),
}));

vi.mock('./claimConfigApi', () => ({
  claimConfigApi: { listHospitals, listDiseases, listDrugs, listFeeCategories },
}));

import { loadStandardCodeOptions } from './standardCodeOptions';

/** 造 n 条医院记录（回归只需条数与编码）。 */
const hospitals = (from: number, n: number) =>
  Array.from({ length: n }, (_, i) => ({ hospitalCode: `H${from + i}`, hospitalName: `医院${from + i}` }));

describe('loadStandardCodeOptions（映射标准码下拉）', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('单页未满即结束，label 含名称与编码', async () => {
    listHospitals.mockResolvedValue({ data: { records: hospitals(1, 3), total: 3 } });
    const r = await loadStandardCodeOptions('HOSPITAL');
    expect(r.truncated).toBe(false);
    expect(r.options).toEqual([
      { label: '医院1(H1)', value: 'H1' },
      { label: '医院2(H2)', value: 'H2' },
      { label: '医院3(H3)', value: 'H3' },
    ]);
    expect(listHospitals).toHaveBeenCalledTimes(1);
  });

  it('回归：>200 条逐页取全，不再静默截断', async () => {
    const total = 200 + 50;
    listHospitals.mockImplementation(async ({ page, size }: { page: number; size: number }) => {
      const start = (page - 1) * size + 1;
      const n = Math.min(size, total - start + 1);
      return { data: { records: hospitals(start, Math.max(n, 0)), total } };
    });
    const r = await loadStandardCodeOptions('HOSPITAL');
    expect(r.options).toHaveLength(total);
    expect(r.truncated).toBe(false);
    expect(listHospitals).toHaveBeenCalledTimes(3);
    expect(listHospitals).toHaveBeenCalledWith({ page: 1, size: LIBRARY_PAGE_SIZE });
  });

  it('触达页数上限时显式标记 truncated（不静默返回部分数据）', async () => {
    listHospitals.mockImplementation(async () => ({ data: { records: hospitals(1, LIBRARY_PAGE_SIZE), total: 999_999 } }));
    const r = await loadStandardCodeOptions('HOSPITAL');
    expect(r.truncated).toBe(true);
    expect(r.options).toHaveLength(LIBRARY_MAX_PAGES * LIBRARY_PAGE_SIZE);
  });

  it('四个目录域均走取全路径，未知域返回空', async () => {
    listDiseases.mockResolvedValue({ data: { records: [{ diseaseCode: 'D1', diseaseName: '疾病1' }], total: 1 } });
    listDrugs.mockResolvedValue({ data: { records: [{ drugCode: 'R1', drugName: '药品1' }], total: 1 } });
    listFeeCategories.mockResolvedValue({ data: { records: [{ feeCode: 'F1', feeName: '费用1' }], total: 1 } });
    expect((await loadStandardCodeOptions('DISEASE')).options).toEqual([{ label: '疾病1(D1)', value: 'D1' }]);
    expect((await loadStandardCodeOptions('DRUG')).options).toEqual([{ label: '药品1(R1)', value: 'R1' }]);
    expect((await loadStandardCodeOptions('FEE_CATEGORY')).options).toEqual([{ label: '费用1(F1)', value: 'F1' }]);
    expect(await loadStandardCodeOptions('CERT_TYPE' as never)).toEqual({ options: [], truncated: false });
  });
});
