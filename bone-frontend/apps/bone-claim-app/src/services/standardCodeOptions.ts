/**
 * 基础目录库 → 主数据映射的标准码下拉选项。
 *
 * <p>从 `MappingPage` 抽出，使「映射必须指向存在的标准码」这一约束可独立单测。
 * 映射页此前固定 `size: 200` 取单页，目录超过 200 条时静默截断——配置岗在下拉里选不到
 * 后面的医院/疾病/药品/费用类别，界面不报错，只表现为「选不到这个码」。
 * 本模块统一走 {@link fetchAllPages} 逐页取全，触达硬上限时以 `truncated` 显式返回，
 * 由调用方告警，绝不静默返回部分数据（与 `LiabilityConfigPage`/`usePolicyOptions` 同构）。
 */
import { claimConfigApi, type MappingDomain } from './claimConfigApi';
import { fetchAllPages } from '../utils/libraryPaging';

export interface CodeOption {
  label: string;
  value: string;
}

export interface StandardCodeOptionsResult {
  options: CodeOption[];
  /** 触达页数硬上限仍未取尽：调用方必须显式告警，不得当作完整数据。 */
  truncated: boolean;
}

/** 按域逐页取全标准码并转成下拉选项。未知域返回空列表，不告警。 */
export async function loadStandardCodeOptions(domain: MappingDomain): Promise<StandardCodeOptionsResult> {
  const empty: StandardCodeOptionsResult = { options: [], truncated: false };
  switch (domain) {
  case 'HOSPITAL': {
    const r = await fetchAllPages((page, size) => claimConfigApi.listHospitals({ page, size }));
    return {
      options: r.rows.map((x) => ({ label: `${x.hospitalName}(${x.hospitalCode})`, value: x.hospitalCode })),
      truncated: r.truncated,
    };
  }
  case 'DISEASE': {
    const r = await fetchAllPages((page, size) => claimConfigApi.listDiseases({ page, size }));
    return {
      options: r.rows.map((x) => ({ label: `${x.diseaseName}(${x.diseaseCode})`, value: x.diseaseCode })),
      truncated: r.truncated,
    };
  }
  case 'DRUG': {
    const r = await fetchAllPages((page, size) => claimConfigApi.listDrugs({ page, size }));
    return {
      options: r.rows.map((x) => ({ label: `${x.drugName}(${x.drugCode})`, value: x.drugCode })),
      truncated: r.truncated,
    };
  }
  case 'FEE_CATEGORY': {
    const r = await fetchAllPages((page, size) => claimConfigApi.listFeeCategories({ page, size }));
    return {
      options: r.rows.map((x) => ({ label: `${x.feeName}(${x.feeCode})`, value: x.feeCode })),
      truncated: r.truncated,
    };
  }
  default:
    return empty;
  }
}
