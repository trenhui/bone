import request from "@/utils/request";

const BASE_URL = "/data-api";

class FlowAPI {
  static pageInit(data: { bizIdentityCode: string }) {
    return request<any, TPAFlowConfig>({
      url: `${BASE_URL}/tpa/claim/flow/pageInit`,
      method: "get",
      params: data,
    });
  }

  /**
   * 保存流程配置
   * @param data
   * @returns
   */
  static saveFlowConfig(bizIdentityCode: string, config: FlowConfigVO) {
    return request<any, any>({
      url: `${BASE_URL}/tpa/claim/flow/saveFlowConfig`,
      method: "post",
      data: {
        bizIdentityCode,
        config,
      },
    });
  }

  /**
   * 保存初审配置
   * @param data
   * @returns
   */
  static savePreCheckConfig(bizIdentityCode: string, config: PreCheckConfigVO) {
    return request<any, any>({
      url: `${BASE_URL}/tpa/claim/flow/savePreCheckConfigVO`,
      method: "post",
      data: {
        bizIdentityCode,
        config,
      },
    });
  }

  /**
   * 保存录入配置
   * @param data
   * @returns
   */
  static saveInputConfig(bizIdentityCode: string, config: InputConfigVO) {
    return request<any, any>({
      url: `${BASE_URL}/tpa/claim/flow/saveInputCheckConfigVO`,
      method: "post",
      data: {
        bizIdentityCode,
        config,
      },
    });
  }

  /**
   * 保存质检配置
   * @param data
   * @returns
   */
  static saveQualityConfig(bizIdentityCode: string, config: QualityConfigVO) {
    return request<any, any>({
      url: `${BASE_URL}/tpa/claim/flow/saveQualityCheckConfigVO`,
      method: "post",
      data: {
        bizIdentityCode,
        config,
      },
    });
  }

  /**
   * 保存审核配置
   * @param data
   * @returns
   */
  static saveApproveConfig(bizIdentityCode: string, config: ApproveConfigVO) {
    return request<any, any>({
      url: `${BASE_URL}/tpa/claim/flow/saveApproveConfigVO`,
      method: "post",
      data: {
        bizIdentityCode,
        config,
      },
    });
  }

  /**
   * 保存复核配置
   * @param data
   * @returns
   */
  static saveApproveCheckConfig(
    bizIdentityCode: string,
    config: ApproveCheckConfigVO
  ) {
    return request<any, any>({
      url: `${BASE_URL}/tpa/claim/flow/saveApproveCheckConfigVO`,
      method: "post",
      data: {
        bizIdentityCode,
        config,
      },
    });
  }

  /**
   * 发布流程配置
   * @param data
   * @returns
   */
  static deployFlowConfig(bizIdentityCode: string) {
    return request<any, any>({
      url: `${BASE_URL}/tpa/claim/flow/deployFlowConfig?bizIdentityCode=${bizIdentityCode}`,
      method: "post",
    });
  }
}

export default FlowAPI;

export interface Condition {
  beanName: string;
  name: string;
  script: string;
  status: number;
  type: number;
}

export interface JumpType {
  conditionList: Condition[];
  name: string;
  status: number;
  type: string;
}

/**
 * 流程配置
 */
export interface FlowConfigVO {
  preCheckFlowNode: {
    jumpTypeList: JumpType[];
  };
  inputFlowNode: {
    jumpTypeList: JumpType[];
  };
  qualityFlowNode: {
    jumpTypeList: JumpType[];
  };
  approveFlowNode: {
    jumpTypeList: JumpType[];
  };
  approveCheckFlowNode: {
    jumpTypeList: JumpType[];
  };
}

/**
 * 初审配置
 */
export interface PreCheckConfigVO {
  /** 是否自动分类 0 否 1 是 */
  autoCategoryImage: string;
  /** 是否自动化 0 否 1 是 */
  autoTag: string;
  /** 影像分类规则 0 有影像件分类即可(分类的影像件>=1) 1 所有影像件均分类 2 无需强制分 */
  categoryRule: string;
  /** 影像分类方式 0 先自动分类再人工确认 1 仅自动分类 2 仅人工分类 */
  categoryType: string;
  /** 处理人分配策略 0 手工分配 1 随机分配 */
  dealerAssignType: string;
}

/**
 * 录入配置
 */
export interface InputConfigVO {
  /** 是否自动化 0 否 1 是 */
  autoTag: string;
  /** 处理人分配策略 0 手工分配 1 随机分配 */
  dealerAssignType: string;
  /** 发票录入方式 0 先技力再传统录入 1 仅传统录入 2 仅技力录入(在质检传统录入) */
  inputType: string;
  /** 发票录入类型 0 限发票层 1 发票层和费用明细层(需校验) 2 发票层和费用明细层(不校验) */
  invoiceDeepType: string;
  /** 发票关联影像 0 否 1 是 */
  invoiceImageBind: string;
}

/**
 * 质检配置
 */
export interface QualityConfigVO {
  /** 处理人分配策略 0 手工分配 1 随机分配 */
  dealerAssignType: string;
  /** 发票录入类型 0 限发票层 1 发票层和费用明细层(需校验) 2 发票层和费用明细层(不校验) */
  invoiceDeepType: string;
  /** 是否支持选保单或责任 0 不支持 1 选保单不选责任 2 选保单和责任 */
  liabilityBindType: string;
}

/**
 * 审核配置
 */
export interface ApproveConfigVO {
  /** 自动化审核标记 0 不支持 1 支持 */
  autoApproveTag: string;
  /** 是否支持修改赔付金额 0 不支持 1 支持 */
  canModifyMoneyTag: string;
  /** 处理人分配策略 0 手工分配 1 随机分配 */
  dealerAssignType: string;
  /** 发票录入类型 0 限发票层 1 发票层和费用明细层(需校验) 2 发票层和费用明细层(不校验) */
  invoiceDeepType: string;
  /** 同人同保单理算方式 0 审核环境逐案人工理算 1 审核前逐案理算 2 审核前批量逐案自动理算 */
  onePersonOnePolicyTag: string;
  /** 是否支持多保单理算 0 不支持 1 支持 */
  policyMoreCalTag: string;
  /** 是否支持快捷理算 0 不支持 1 支持 */
  quickCallTag: string;
}

/**
 * 复核配置
 */
export interface ApproveCheckConfigVO {
  /** 分配人员策略 0 手工分配 1 随机分配 */
  dealerAssignType: string;
}

export interface TPAFlowConfig {
  config: {
    approveCheckConfigVO: ApproveCheckConfigVO;
    approveConfigVO: ApproveConfigVO;
    flowConfigVO: FlowConfigVO;
    inputConfigVO: InputConfigVO;
    preCheckConfigVO: PreCheckConfigVO;
    qualityConfigVO: QualityConfigVO;
    bizIdentityCode: string;
  };
}
