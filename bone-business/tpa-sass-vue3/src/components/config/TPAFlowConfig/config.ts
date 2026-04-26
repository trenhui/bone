export const Config = {
  precheck: {
    headers: ["初审作业规则项", "规则选项值"],
    items: [
      {
        label: "影像分类规则",
        key: "categoryRule",
        options: [
          {
            label: "有影像件分类即可(分类的影像件>=1)",
            value: "0",
          },
          {
            label: "所有影像件均分类",
            value: "1",
          },
          {
            label: "无需强制分类",
            value: "2",
          },
        ],
      },
      {
        label: "影像分类方式",
        key: "categoryType",
        options: [
          {
            label: "先自动分类再人工确认",
            value: "0",
          },
          {
            label: "仅自动分类",
            value: "1",
          },
          {
            label: "仅人工分类",
            value: "2",
          },
        ],
      },
    ],
  },
  precheckAuto: {
    headers: ["初审自动化规则项", "规则选项值"],
    items: [
      {
        label: "是否自动化初审",
        key: "autoTag",
        options: [
          {
            label: "是",
            value: "1",
          },
          {
            label: "否",
            value: "0",
          },
        ],
      },
      {
        label: "是否自动分类",
        key: "autoCategoryImage",
        options: [
          {
            label: "是",
            value: "1",
          },
          {
            label: "否",
            value: "0",
          },
        ],
      },
      {
        label: "处理人分配策略",
        key: "dealerAssignType",
        options: [
          {
            label: "手工分配",
            value: "0",
          },
          {
            label: "随机分配",
            value: "1",
          },
        ],
      },
    ],
  },
  input: {
    headers: ["录入作业规则项", "规则选项值"],
    items: [
      {
        label: "发票录入类型",
        key: "invoiceDeepType",
        options: [
          {
            label: "限发票层",
            value: "0",
          },
          {
            label: "发票层和费用明细层(需校验)",
            value: "1",
          },
          {
            label: "发票层和费用明细层(不校验)",
            value: "2",
          },
        ],
      },
      {
        label: "发票录入方式",
        key: "inputType",
        options: [
          {
            label: "先技力再传统录入",
            value: "0",
          },
          {
            label: "仅传统录入",
            value: "1",
          },
          {
            label: "仅技力录入(在质检传统录入)",
            value: "2",
          },
        ],
      },
      {
        label: "发票关联影像",
        key: "invoiceImageBind",
        options: [
          {
            label: "是",
            value: "1",
          },
          {
            label: "否",
            value: "0",
          },
        ],
      },
    ],
  },
  inputAuto: {
    headers: ["录入自动化规则项", "规则选项值"],
    items: [
      {
        label: "是否自动化录入",
        key: "autoTag",
        options: [
          {
            label: "是",
            value: "1",
          },
          {
            label: "否",
            value: "0",
          },
        ],
      },
      {
        label: "处理人分配策略",
        key: "dealerAssignType",
        options: [
          {
            label: "手工分配",
            value: "0",
          },
          {
            label: "随机分配",
            value: "1",
          },
        ],
      },
    ],
  },
  quality: {
    headers: ["质检作业规则项", "规则选项值"],
    items: [
      {
        label: "发票录入类型",
        key: "invoiceDeepType",
        options: [
          {
            label: "限发票层",
            value: "0",
          },
          {
            label: "发票层和费用明细层(需校验)",
            value: "1",
          },
          {
            label: "发票层和费用明细层(不校验)",
            value: "2",
          },
        ],
      },
      {
        label: "是否支持选保单或责任",
        key: "liabilityBindType",
        options: [
          {
            label: "不支持",
            value: "0",
          },
          {
            label: "选保单不选责任",
            value: "1",
          },
          {
            label: "选保单和责任",
            value: "2",
          },
        ],
      },
      {
        label: "发票关联影像",
        key: "invoiceImageBind",
        options: [
          {
            label: "是",
            value: "1",
          },
          {
            label: "否",
            value: "0",
          },
        ],
      },
    ],
  },
  qualityAuto: {
    headers: ["质检自动化规则项", "规则选项值"],
    items: [
      {
        label: "处理人分配策略",
        key: "dealerAssignType",
        options: [
          {
            label: "手工分配",
            value: "0",
          },
          {
            label: "随机分配",
            value: "1",
          },
        ],
      },
    ],
  },
  approve: {
    headers: ["审核作业规则项", "规则选项值"],
    items: [
      {
        label: "同人同保单理算方式",
        key: "onePersonOnePolicyTag",
        options: [
          {
            label: "审核环境逐案人工理算",
            value: "0",
          },
          {
            label: "审核前逐案理算",
            value: "1",
          },
          {
            label: "审核前批量逐案自动理算",
            value: "2",
          },
        ],
      },
      {
        label: "发票录入类型",
        key: "invoiceDeepType",
        options: [
          {
            label: "限发票层",
            value: "0",
          },
          {
            label: "发票层和费用明细层(需校验)",
            value: "1",
          },
          {
            label: "发票层和费用明细层(不校验)",
            value: "2",
          },
        ],
      },
      {
        label: "支持多保单理算",
        key: "policyMoreCalTag",
        options: [
          {
            label: "不支持",
            value: "0",
          },
          {
            label: "支持",
            value: "1",
          },
        ],
      },
      {
        label: "支持修改赔付金额",
        key: "canModifyMoneyTag",
        options: [
          {
            label: "不支持",
            value: "0",
          },
          {
            label: "支持",
            value: "1",
          },
        ],
      },
      {
        label: "支持快捷理算",
        key: "quickCallTag",
        options: [
          {
            label: "不支持",
            value: "0",
          },
          {
            label: "支持",
            value: "1",
          },
        ],
      },
    ],
  },
  approveAuto: {
    headers: ["审核自动化规则项", "规则选项值"],
    items: [
      {
        label: "是否自动化审核",
        key: "autoApproveTag",
        options: [
          {
            label: "是",
            value: "1",
          },
          {
            label: "否",
            value: "0",
          },
        ],
      },
      {
        label: "处理人分配策略",
        key: "dealerAssignType",
        options: [
          {
            label: "手工分配",
            value: "0",
          },
          {
            label: "随机分配",
            value: "1",
          },
        ],
      },
    ],
  },
  approveCheck: {
    headers: ["复核作业规则项", "规则选项值"],
    items: [],
  },
  approveCheckAuto: {
    headers: ["复核自动化规则项", "规则选项值"],
    items: [
      {
        label: "处理人分配策略",
        key: "dealerAssignType",
        options: [
          {
            label: "手工分配",
            value: "0",
          },
          {
            label: "随机分配",
            value: "1",
          },
        ],
      },
    ],
  },
};
