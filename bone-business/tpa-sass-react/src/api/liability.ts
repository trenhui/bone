import request from "@/utils/request";

const BASE_URL = "/data-api";

class LiabilityAPI {
  static queryLiabilityList(policyNo: string) {
    return request<any, LiabilityObject[]>({
      url: `${BASE_URL}/tpa/adjust/plan/queryPlanByPolicy`,
      method: "get",
      params: { policyNo },
    });
  }

  static createPlan(data: PlanDTO[]) {
    return request({
      url: `${BASE_URL}/tpa/adjust/plan/createPlanList`,
      method: "post",
      data,
    });
  }

  static updatePlan(data: { planDTO: PlanDTO; remark: string }) {
    return request({
      url: `${BASE_URL}/tpa/adjust/plan/updatePlan`,
      method: "post",
      data,
    });
  }

  // static deletePlan(data: any) {
  //   return request({
  //     url: `${BASE_URL}/plan/delete`,
  //     method: "post",
  //     data,
  //   });
  // }

  static deployPlan(planId: string) {
    return request({
      url: `${BASE_URL}/tpa/adjust/liability/deployPlan`,
      method: "get",
      params: { planId },
    });
  }

  static createCoverage(data: CoverageDTO[]) {
    return request({
      url: `${BASE_URL}/tpa/adjust/plan/createCoverage`,
      method: "post",
      data: { coverageDTOList: data },
    });
  }

  static updateCoverage(data: { coverageDTO: CoverageDTO; remark: string }) {
    return request({
      url: `${BASE_URL}/tpa/adjust/plan/updateCoverage`,
      method: "post",
      data,
    });
  }

  // static deleteCoverage(data: any) {
  //   return request({
  //     url: `${BASE_URL}/coverage/delete`,
  //     method: "post",
  //     data,
  //   });
  // }

  static createLiability(data: {
    policyNo: string;
    liabilityCreateRequestList: Pick<
      LiabilityDTO,
      "coverageId" | "liabilityName" | "planId"
    >[];
  }) {
    return request({
      url: `${BASE_URL}/tpa/adjust/liability/create`,
      method: "post",
      data,
    });
  }

  static queryLiabilityDetail(id: string) {
    return request<any, any>({
      url: `${BASE_URL}/tpa/adjust/liability/liabilityEditPageInit`,
      method: "get",
      params: { id },
    });
  }

  static saveLiabilityConfig(data: LiabilityDTO) {
    return request({
      url: `${BASE_URL}/tpa/adjust/liability/saveLiabilityConfig`,
      method: "post",
      data,
    });
  }

  /**
   * 生成理算公式
   */
  static generateFormula(data: LiabilityDTO) {
    return request<any, any>({
      url: `${BASE_URL}/tpa/adjust/liability/formula`,
      method: "post",
      data,
    });
  }

  /**
   * 共保相关接口
   */
  static shareConfigPageInit(policyNo: string) {
    return request<any, any>({
      url: `${BASE_URL}/tpa/adjust/liability/share/shareConfigPageInit`,
      method: "get",
      params: { policyNo },
    });
  }

  static createShareCode(data: ShareDTO[]) {
    return request({
      url: `${BASE_URL}/tpa/adjust/liability/share/createShareCode`,
      method: "post",
      data: { dtoList: data },
    });
  }

  static updateShareCodeLimit(data: Pick<ShareDTO, "id" | "shareLimit">) {
    return request({
      url: `${BASE_URL}/tpa/adjust/liability/share/modifyShareCodeLimit`,
      method: "post",
      data,
    });
  }

  static deleteShareCode(params: { id: string }) {
    return request({
      url: `${BASE_URL}/tpa/adjust/liability/share/deleteShareCode`,
      method: "get",
      params,
    });
  }

  static createShareRelation(data: ShareMemberDTO[]) {
    return request({
      url: `${BASE_URL}/tpa/adjust/liability/share/createRelation`,
      method: "post",
      data: { dtoList: data },
    });
  }

  static deleteShareRelation(params: { id: string }) {
    return request({
      url: `${BASE_URL}/tpa/adjust/liability/share/deleteRelation`,
      method: "get",
      params,
    });
  }

  /**
   * 先赔与后赔责任接口
   */
  static nextRelationPageInit(policyNo: string) {
    return request<any, any>({
      url: `${BASE_URL}/tpa/adjust/liability/sort/pageInit`,
      method: "get",
      params: { policyNo },
    });
  }

  static createNextRelation(data: NextDTO) {
    return request({
      url: `${BASE_URL}/tpa/adjust/liability/sort/saveSortRelations`,
      method: "post",
      data,
    });
  }

  static deleteNextRelation(params: { id: string }) {
    return request({
      url: `${BASE_URL}/tpa/adjust/liability/sort/deleteSortRelation`,
      method: "get",
      params,
    });
  }

  /**
   * 查询保单下的责任推送配置
   * @param policyNo
   * @returns
   */
  static getLiabilityPushConfig(policyNo: string) {
    return request<any, LiabilityPushConfigDTO[]>({
      url: `${BASE_URL}/tpa/liabilityMapping/getLiabilityMapping`,
      method: "get",
      params: { policyNo },
    });
  }

  /**
   * 更新单条责任映射数据
   * @param data
   * @returns
   */
  static updateLiabilityPushConfig(data: LiabilityPushConfigUpdateDTO) {
    return request({
      url: `${BASE_URL}/tpa/liabilityMapping/updateLiabilityMapping`,
      method: "post",
      data,
    });
  }

  /**
   * 批量更新责任映射数据
   * @param data
   * @returns
   */
  static batchUpdateLiabilityMapping(data: LiabilityPushConfigUpdateDTO[]) {
    return request({
      url: `${BASE_URL}/tpa/liabilityMapping/batchUpdateLiabilityMapping`,
      method: "post",
      data,
    });
  }

  /**
   * 关联保单
   * @param claimId
   * @param policyNo
   * @param planUuid
   * @returns
   */
  static bind(claimId: string, row: any) {
    return request({
      url: `${BASE_URL}/tpa/adjust/bind`,
      method: "post",
      data: { claimId, policyInfoModel: row },
    });
  }

  /**
   * 开始理算
   * @param claimId
   * @returns
   */
  static adjust(claimId: string) {
    return request({
      url: `${BASE_URL}/tpa/adjust/adjust`,
      method: "get",
      params: { claimId },
    });
  }

  /**
   * 清除理算
   * @param claimId
   * @returns
   */
  static clearAdjust(claimId: string) {
    return request({
      url: `${BASE_URL}/tpa/adjust/clear`,
      method: "get",
      params: { claimId },
    });
  }

  /**
   * 审核页面查询关联责任
   * @param claimId
   * @returns
   */
  static queryRelatedLiability(
    claimId: string,
    visitTypeCn: string | null = null
  ) {
    return request<any, any>({
      url: `${BASE_URL}/tpa/adjust/queryliability`,
      method: "get",
      params: { claimId, visitTypeCn },
    });
  }
}

export default LiabilityAPI;

export interface PlanDTO {
  /** 计划id */
  id: string;
  /** 保单号 */
  policyNo: string;
  /** 计划名称 */
  planName: string;
  /** 计划code */
  planCode: string;
  /** 计划额度 */
  planLimit: number;
  /** 版本 */
  version?: string;
  /** 版本状态 DRAFT草稿 ACTIVE已发布 DOWN已废弃 */
  status?: string;
  // /** 更新信息 */
  // remark?: string;

  /** 是否编辑 仅用于前端 */
  isEditing?: boolean;
  /** 计划额度类型 1-有限额 -1-无限额 仅用于前端 */
  limitType?: number;
}

export interface CoverageDTO {
  /** 险种id */
  id: string | null;
  /** 保单号 */
  policyNo: string;
  /** 关联的计划id */
  planId: string;
  /** 险种名称 */
  coverageName: string;
  /** 险种code */
  coverageCode: string;
  /** 险种额度 */
  coverageLimit: number;
  /** 版本 */
  version?: string;

  /** 是否编辑 仅用于前端 */
  isEditing?: boolean;
  /** 是否有限额 仅用于前端 */
  limitType?: number;
}

export interface LiabilityObject {
  /** 计划 */
  plan: PlanDTO;
  /** 险种 */
  coverage?: CoverageDTO;
  /** 责任 */
  liability?: Pick<
    LiabilityDTO,
    "id" | "policyNo" | "planId" | "coverageId" | "liabilityName"
  >;
}

export type LiabilityShortDTO = Pick<
  LiabilityDTO,
  /** 责任UUID */
  "id" | "uuid" | "policyNo" | "planId" | "coverageId" | "liabilityName"
>;

export type LiabilityNextDTO = LiabilityShortDTO & {
  /** 后付责任的uuid */
  nextLiabilityUuid: string;
  /** 后付关系的类型TYPE1, 先付责任比例外也赔TYPE2, 先付责任比例外不赔TYPE3, 先付责任为0才赔 */
  nextLiabilityType: string;
};

export interface LiabilityDTO {
  /** id */
  id: string;
  /** 责任UUID */
  uuid: string;
  /** 保单号 */
  policyNo: string;
  /** 关联的计划id */
  planId: string;
  /** 关联的险种Id */
  coverageId: string;
  /** 责任名称 */
  liabilityName: string;
  /** 责任形式 */
  liabilityType: string;
  /** 版本 */
  version?: string;
  /** 参与的共保关系列表数组内容是共保关系的id */
  shareId: string[];
  /** 后付责任的uuid */
  nextLiabilityUuid: string;
  /** 后付关系的类型TYPE1, 先付责任比例外也赔TYPE2, 先付责任比例外不赔TYPE3, 先付责任为0才赔 */
  nextLiabilityType: string;
  /** 是否能被设置为发票关联责任 */
  invoiceRelateAble: boolean;
  /** 给付依据（定额给付使用）SEVERE重疾标识DISEASE疾病种类DISABILITY失能标识 */
  paymentBasis: string;
  /** 津贴细则（津贴给付使用）。存储在数据库中时转为json。 */
  allowanceDetail: AllowanceDetail;
  /** 适用对象 不指定时为null */
  restrictObject: RestrictObject | null;
  /** 适用限定 */
  restrictScope: RestrictScope[] | null;
  /** 适用出险 */
  restrictOutInsure: RestrictOutInsure;
  /** 等待期。不需要等待时输入-1。 */
  waitingPeriod: number;
  /** 等待期类型 仅用于前端*/
  waitingPeriodType?: number;
  /** 赔付比例 */
  payPercent: PayPercent;
  /** 责任免赔 */
  liabilityDeduct: LiabilityDeduct | null;
  /** 次期限额 */
  timesLimit: TimesLimit | null;
  /** 责任账户类型PERSONAL个账PUBLIC公账 */
  accountType: string;
  /** 控额方式 */
  quotaController: QuotaController;
  /** 保额类型PRESET 预设保额PERSONAL 个单保额（本次暂不包含） */
  insuranceQuotaType: string;
  /** 责任额度 */
  liabilityLimit: LiabilityLimit;
  /** 理算信息 */
  adjustmentDetail: AdjustmentDetail[];
  /** 理算公式 */
  formula: string;
  /** 更新信息 */
  remark: string;
}

/** 津贴细则 */
export interface AllowanceDetail {
  /** 开始日期非保险或等待期期间怎么处理 */
  startOutPeriod: string;
  /** 日津贴金额 */
  allowancePerDay: number;
  /** 期间天数上限 */
  inPeriodLimit: number;
  /** 期满天数上限 */
  outPeriodLimit: number;
  /** 津贴天数计算方式 */
  dayCountOption: string;
}

/** 适用对象 */
export interface RestrictObject {
  /** 性别 */
  gender: string | null;
  /** 年龄 */
  age: RangeObject | null;
  /** 职业 */
  occupation: string[] | null;
  /** 其他 */
  other: string | null;
}

/** 适用对象区间 */
export interface RangeObject {
  /** 下限 */
  lowerLimit: number;
  /** 上限 */
  upperLimit: number;
  /** 闭包区间类型 LEFT_OPEN 左开右闭 RIGHT_OPEN 左闭右开 BOTH_OPEN 左开右开 BOTH_CLOSED 左闭右闭 */
  intervalType: string;

  /** 是否编辑 仅用于前端界面展示 */
  isEditing?: boolean;
}

/** 适用限定 */
export interface RestrictScope {
  /** 范围限定 HOSPITAL医院限定 DRUG药品限定 DIAGNOSE诊疗限定 DISEASE病种限定 */
  restrictRange: string;
  /** 是否启用 */
  open: boolean;
  /** 限定方式 WHITE白名单 BLACK黑名单 */
  type: string;
  /** 限定清单 */
  restrictList: string[];
}

/** 适用出险 */
export interface RestrictOutInsure {
  /** 适用出险 HOSPITAL医疗 */
  type: string[];
  /** 就诊类型 key是就诊类型 value是适用发票医疗类型 */
  visitType: string[];
  /** 医保使用情形 AFTER 医保后才能赔付、BEFORE 未使用医保赔付、BOTH 两种都能赔付 */
  medicalInsurance?: string;
  /** 发票费用类型 */
  invoiceFeeType?: InvoiceFeeType[];
  /** 承担费用类型 */
  liabilityFeeType?: InvoiceFeeType[];
  /** 意外类型 */
  accidentType: string[];
  /** 伤残等级 */
  disabilityLevel: string;
}

/** 费用类型 */
export interface InvoiceFeeType {
  /** 是否默认 */
  isDefault: boolean;
  /** 金额类型 */
  feeType: string;
  /** 是否使用/承担 */
  open: boolean;
  /** 来源方式 INPUT录入项、CALCULATE计算项 */
  source: string;
  /** 计算项表达式 */
  calculateFormula: string;
  /** 父费用类型 */
  parentFeeType?: string;

  /** 是否展开 仅用于前端界面展示 */
  isExpanded?: boolean;
}

/** 赔付比例 */
export interface PayPercent {
  /** 赔付比例类型 SAME同一比例、DIFFERENT不同比例 */
  type: string;
  /** 同一比例，比如0.15存储15 */
  percent: number;
  /** 不同比例因素 MEDICAL_INSURANCE区分医保赔付、HOSPITAL_LEVEL区分医院等级、HOSPITAL_TYPE区分医院性质、SEVERENESS区分程度比例、AGE区分年龄区间、TOTAL_FEE发票总费用区间、ADJUSTMENT_FEE案件理算金额区间、CLAIM_TIMES赔案累计区间、PAY_TIMES区分赔付次数 */
  factor: string[];
  /** 区间设置。RangeObject里包含四个变量，分别是区间类型，范围的上下限和左右取值。 */
  rangeMap: Record<string, RangeObject[]>;
}

/** 责任免赔 */
export interface LiabilityDeduct {
  /** 免赔模式 UNIVERSAL统一额度、PERSONAL个人免赔额度、DIRECT直付免赔额度 */
  deductPattern: string;
  /** 免赔类型 ABSOLUTE绝对免赔、RELATIVE相对免赔 */
  deductType: string;
  /** 免赔形式 MONEY免赔金额、RATIO免赔比例、DAYS免赔天数 */
  deductMode: string;
  /** 免赔方式 ANNUAL按年度、TIMES按次数 */
  deductPeriod: string;
  /** 免赔对象 ADJUST理算金额、INVOICE发票费用 */
  deductTarget: string;
  /** 免赔抵扣 MEDICAL_THIRD医保及三方可抵扣、THIRD医保不可抵扣，三方可抵扣、NO均不可抵扣 */
  deductRule: string;
  /** 是否不同免赔 SOCIAL_SECURITY区分是否社保、AGE区分年龄区间、HOSPITAL_LEVEL区分医院等级、HOSPITAL_TYPE区分医院性质、LIABILITY_FEE区分承担费用类型、PERSONAL_AMOUNT个人赔付额度 */
  factor: string[];
  /**
   * valueList：Map<String, Integer>
   * value为免赔金额/比例/天数，根据形式变化。
   * key为不同免赔因素的值连接起来。
   * 例如"HOSPITAL_LEVEL=一级,HOSPITAL_TYPE=公立"，list<string>转string字符串以,分割作为key。
   * 其顺序与factor的顺序相同。若无不同免赔，key为DEFAULT。
   */
  valueList: Record<string, number>;
}

/** 次期限额 */
export interface TimesLimit {
  /** 限额方式 TIMES按次 DAYS按日 */
  type: string;
  /** 次期定义 CLAIM一赔案记一次 DAY发票同日记一次 HOSPITAL发票同日同院记一次 DEPARTMENT发票同日同院同科室记一次 DISEASE发票同日同院同病记一次 CLAIM_SAME_DAY按同日赔案累计 INVOICE_SAME_DAY按同日发票累计 */
  definition: string;
  /** 控制方式 FORCE强制限额 ALARM告知，仅弹窗提醒 */
  controlType: string;
  /** 次期额度 */
  amount: number | null;
}

/** 控额方式 */
export interface QuotaController {
  /** 控额方式 TPA 普康TPA控额 DIRECT 普康支付控额 INSURER 保司三方接口*/
  type: string;
  /** 支付保单号 */
  policyNo: string;
}

/** 责任额度 */
export interface LiabilityLimit {
  /** 控额方式 LIABILITY责任额度 LIABILITY_VISIT责任额度+就诊类别 LIABILITY_PERSONAL责任额度+个人额度 PERSONAL个人额度 FIXED_AMOUNT给付基础额度 */
  type: string;
  /** 责任额度 */
  liabilityLimit: number | null;
  /** 门急诊额度 */
  outpatientEmergencyLimit: number | null;
  /** 住院额度 */
  inpatientLimit: number | null;
  /** 药房额度 */
  pharmacyLimit: number | null;
  /** 门诊慢特病额度 */
  specialClinicLimit: number | null;
  /** 给付基础额度 */
  fixedAmount: number | null;
}

/** 理算信息 */
export interface AdjustmentDetail {
  /** 承担费用类型 */
  liabilityFeeType: string;
  /** 是否医保 true|false */
  hasYb: string;
  /** 费用限额 */
  liabilityLimit: number | null;
  /** 赔付比例 */
  valueList: Record<string, number | null>;
}

/** 共保关系 */
export interface ShareDTO {
  id: string | null;
  /** 保单号 */
  policyNo: string;
  /** 计划id */
  planId: string;
  /** 计划名称 */
  planName: string;
  /** 责任共保id */
  shareId: string;
  /** 责任共保Code */
  shareCode: string;
  /** 责任共保额度 */
  shareLimit: number;
  /** 版本 */
  version?: string;
  /** 更新信息 */
  remark?: string;

  /** 是否编辑 仅用于前端 */
  isEditing?: boolean;
}

/** 共保关系成员 */
export interface ShareMemberDTO {
  /** 责任UUID */
  liabilityUuid: string;
  /** 计划id */
  planId: string;
  /** 保单号 */
  policyNo: string;
  /** 共保code */
  shareCode: string;

  /** 是否编辑 仅用于前端 */
  isEditing?: boolean;
}

export interface ShareRelationDTO {
  id: string;
  /** 共保code */
  shareCode: string;
  /** 共保关系 */
  shareInfo: ShareDTO;
  /** 险种信息 */
  coverageDTO: CoverageDTO;
  /** 计划id */
  planId: string;
  /** 计划信息 */
  planInfo: PlanDTO;
  /** 责任名称 */
  liabilityName: string;
}

/** 新建时的后付责任 */
export interface NextDTO {
  /** 责任uuid */
  id: string;
  /** 后付责任UUID */
  nextUuid: string;
  /** 后付责任的种类 */
  nextType: string;
}

/** 已配置的后付责任 */
export interface NextConfiged {
  coverageId: string;
  nextLiabilityUuid: string;
  nextLiabilityType: string;
  planName: string;
  planId: string;
  id: string;
  nextLiabilityName: string;
  liabilityName: string;

  /** 是否编辑 仅用于前端 */
  isEditing?: boolean;
}

export interface LiabilityPushConfigDTO {
  id: string;
  /** 责任uuid */
  liabilityUuid: string;
  /** 责任名称 */
  liabilityName: string;
  /** 发票医疗类型 */
  invoiceMedicalType: string;
  /** 发票医疗类型中文 */
  invoiceMedicalTypeCN: string;
  /** 保司险种代码 */
  insuranceCompanyCoverage: string;
  /** 保司责任代码 */
  insuranceCompanyLiability: string;
  /** 保司责任子码 */
  insuranceCompanyLiabilitySub: string;
  /** 索赔事故性质 */
  claimAccident: string;
  /** 保司个账公账 */
  insuranceCompanyAccount: string;
  createUser: string;
  updateUser: string;
  createTime: string;
  updateTime: string;

  /** 是否编辑 仅用于前端 */
  isEditing?: boolean;
}

export interface LiabilityPushConfigUpdateDTO {
  id: string;
  insuranceCompanyCoverage: string;
  insuranceCompanyLiability: string;
  insuranceCompanyLiabilitySub: string;
  claimAccident: string;
  insuranceCompanyAccount: string;
}
