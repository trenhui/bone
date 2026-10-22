/* eslint-disable */
// 生成文件：内容由 doc/design/lowcode/cases/ui-schema-samples/*.example.json 脚本生成，勿手改。
/**
 * 内置场景样例（一键载入，用于联调与演示）。
 *
 * 内容与 `doc/design/lowcode/cases/ui-schema-samples/*.example.json` 保持一致 ——
 * 由脚本从该文件生成，不手写，避免两份副本漂移。
 *
 * 用途：新建实体的界面配置时，不必从零拖控件；载入样例后按实体实际字段微调即可。
 * 载入后字段引用若与实体字段不匹配，自检面板会逐条报出（不会静默错配）。
 */
import type { BoneUiSchema } from '@bone/ui-schema';

export interface UiSchemaSample {
  key: string;
  label: string;
  desc: string;
  schema: BoneUiSchema;
}

export const SAMPLE_EXPENSE: BoneUiSchema = {
  "schemaVersion": 1,
  "entityCode": "t_expense_reimbursement",
  "displayModes": [
    "create",
    "edit",
    "view"
  ],
  "views": {
    "list": {
      "columns": [
        {
          "fieldCode": "claim_no",
          "visible": true,
          "width": 140
        },
        {
          "fieldCode": "title",
          "visible": true,
          "width": 220
        },
        {
          "fieldCode": "amount",
          "visible": true,
          "width": 130,
          "format": {
            "currencyField": "currency",
            "precision": 2
          }
        },
        {
          "fieldCode": "category_id",
          "visible": true,
          "width": 120
        },
        {
          "fieldCode": "status",
          "visible": true,
          "width": 100
        },
        {
          "fieldCode": "applicant",
          "visible": true,
          "width": 100
        },
        {
          "fieldCode": "expense_date",
          "visible": true,
          "width": 120
        }
      ],
      "searchFields": [
        "department",
        "status",
        "amount"
      ],
      "tabs": [
        {
          "title": "待我审批",
          "fieldCode": "approver_id",
          "value": "$me"
        },
        {
          "title": "我提交的",
          "fieldCode": "applicant",
          "value": "$me"
        },
        {
          "title": "全部",
          "fieldCode": "status",
          "value": "$all"
        }
      ],
      "dataScope": {
        "policyCode": "owner_or_approver"
      },
      "batchActions": [
        "submit"
      ]
    },
    "form": {
      "sections": [
        {
          "id": "sec_base",
          "title": "基本信息",
          "controls": [
            {
              "fieldCode": "title",
              "controlKind": "INPUT",
              "placeholder": "如：10 月差旅报销"
            },
            {
              "fieldCode": "category_id",
              "controlKind": "REFERENCE",
              "props": {
                "targetEntity": "expense_category",
                "labelField": "name",
                "valueField": "id"
              }
            },
            {
              "fieldCode": "applicant",
              "controlKind": "INPUT",
              "readonly": true
            },
            {
              "fieldCode": "expense_date",
              "controlKind": "DATE"
            }
          ]
        },
        {
          "id": "sec_travel",
          "title": "行程与金额",
          "controls": [
            {
              "fieldCode": "amount",
              "controlKind": "NUMBER",
              "props": {
                "min": 0.01,
                "precision": 2
              }
            },
            {
              "fieldCode": "currency",
              "controlKind": "ENUM"
            },
            {
              "fieldCode": "expense_start",
              "controlKind": "DATE"
            },
            {
              "fieldCode": "expense_end",
              "controlKind": "DATE"
            },
            {
              "fieldCode": "invoice_type",
              "controlKind": "ENUM"
            },
            {
              "fieldCode": "tax_no",
              "controlKind": "INPUT",
              "placeholder": "增值税专票时必填"
            }
          ]
        },
        {
          "id": "sec_items",
          "title": "费用明细",
          "controls": [
            {
              "fieldCode": "items",
              "controlKind": "CHILD_TABLE",
              "props": {
                "childEntity": "expense_reimbursement_item",
                "columns": [
                  "expense_date",
                  "category_id",
                  "amount",
                  "remark"
                ]
              }
            }
          ]
        },
        {
          "id": "sec_files",
          "title": "发票影像",
          "controls": [
            {
              "fieldCode": "attachment",
              "controlKind": "ATTACHMENT",
              "props": {
                "maxCount": 10,
                "maxSizeMB": 10
              }
            }
          ]
        },
        {
          "id": "sec_approval",
          "title": "审批",
          "controls": [
            {
              "fieldCode": "approver_id",
              "controlKind": "REFERENCE",
              "props": {
                "targetEntity": "sys_user",
                "labelField": "name",
                "valueField": "id"
              }
            },
            {
              "fieldCode": "status",
              "controlKind": "ENUM",
              "readonly": true
            }
          ]
        }
      ]
    },
    "detail": {
      "sections": [
        {
          "id": "sec_base",
          "title": "基本信息",
          "controls": [
            {
              "fieldCode": "title"
            },
            {
              "fieldCode": "category_id"
            },
            {
              "fieldCode": "amount"
            },
            {
              "fieldCode": "status"
            }
          ]
        },
        {
          "id": "sec_items",
          "title": "费用明细",
          "controls": [
            {
              "fieldCode": "items",
              "controlKind": "CHILD_TABLE"
            }
          ]
        }
      ]
    }
  },
  "linkageRules": [
    {
      "name": "专票必填税号",
      "when": {
        "fieldCode": "invoice_type",
        "operator": "eq",
        "value": "SPECIAL"
      },
      "then": [
        {
          "fieldCode": "tax_no",
          "visible": true,
          "required": true
        }
      ]
    },
    {
      "name": "结束日期不得早于开始日期",
      "when": {
        "fieldCode": "expense_end",
        "operator": "earlierThan",
        "valueType": "field",
        "compareField": "expense_start"
      },
      "then": [
        {
          "fieldCode": "expense_end",
          "readonly": false,
          "notice": "结束日期早于开始日期，请修正（提交时阻断）"
        }
      ]
    },
    {
      "name": "大额报销必须指定审批人",
      "when": {
        "fieldCode": "amount",
        "operator": "gt",
        "value": 5000
      },
      "then": [
        {
          "fieldCode": "approver_id",
          "visible": true,
          "required": true
        }
      ]
    }
  ],
  "submitRules": [
    {
      "name": "明细合计一致",
      "function": "sum",
      "fields": [
        "items.amount"
      ],
      "operator": "eq",
      "compare": {
        "valueType": "field",
        "fieldCode": "amount"
      },
      "verifyType": "ERROR",
      "message": "费用明细合计必须等于申报金额"
    },
    {
      "name": "单行金额下限",
      "function": "min",
      "fields": [
        "items.amount"
      ],
      "operator": "gte",
      "compare": {
        "valueType": "fixed",
        "value": 0.01
      },
      "verifyType": "ERROR",
      "message": "费用明细单行金额不得小于 0.01（与服务端 min:0.01 同值）"
    },
    {
      "name": "结束日期不得早于开始日期",
      "function": "compare",
      "fields": [
        "expense_end"
      ],
      "operator": "gte",
      "compare": {
        "valueType": "field",
        "fieldCode": "expense_start"
      },
      "verifyType": "ERROR",
      "message": "结束日期不得早于开始日期"
    }
  ],
  "actionBar": [
    {
      "actionCode": "submit",
      "visibleWhen": {
        "fieldCode": "status",
        "operator": "eq",
        "value": "DRAFT"
      },
      "sortOrder": 1
    },
    {
      "actionCode": "approve",
      "visibleWhen": {
        "fieldCode": "status",
        "operator": "eq",
        "value": "SUBMITTED"
      },
      "sortOrder": 2
    },
    {
      "actionCode": "reject",
      "visibleWhen": {
        "fieldCode": "status",
        "operator": "eq",
        "value": "SUBMITTED"
      },
      "sortOrder": 3
    },
    {
      "actionCode": "confirmPayout",
      "visibleWhen": {
        "fieldCode": "status",
        "operator": "eq",
        "value": "APPROVED"
      },
      "sortOrder": 4
    }
  ]
};

export const SAMPLE_CLAIM: BoneUiSchema = {
  "schemaVersion": 1,
  "entityCode": "t_claim_case",
  "displayModes": [
    "create",
    "edit",
    "view"
  ],
  "views": {
    "list": {
      "columns": [
        {
          "fieldCode": "claim_no",
          "visible": true,
          "width": 150
        },
        {
          "fieldCode": "insured_name",
          "visible": true,
          "width": 100
        },
        {
          "fieldCode": "hospital_level",
          "visible": true,
          "width": 110
        },
        {
          "fieldCode": "claim_amount",
          "visible": true,
          "width": 120
        },
        {
          "fieldCode": "payable_amount",
          "visible": true,
          "width": 120
        },
        {
          "fieldCode": "status",
          "visible": true,
          "width": 120
        }
      ],
      "searchFields": [
        "status",
        "hospital_level",
        "claim_amount"
      ],
      "tabs": [
        {
          "title": "待我审核",
          "fieldCode": "reviewer_id",
          "value": "$me",
          "titleKey": "tabs.todo"
        },
        {
          "title": "风险标记",
          "fieldCode": "fraud_flag",
          "value": true
        },
        {
          "title": "已达封顶",
          "fieldCode": "cap_reached",
          "value": true
        }
      ],
      "stats": [
        {
          "title": "今日待办",
          "valueRef": "todo_today",
          "titleKey": "stats.todo_today"
        },
        {
          "title": "快赔自动通过",
          "valueRef": "fasttrack_rate"
        },
        {
          "title": "转人工率",
          "valueRef": "manual_rate"
        }
      ],
      "dataScope": {
        "policyCode": "reviewer_or_owner"
      }
    },
    "form": {
      "sections": [
        {
          "id": "sec_apply",
          "title": "申请信息（员工端）",
          "controls": [
            {
              "fieldCode": "policy_no",
              "controlKind": "INPUT"
            },
            {
              "fieldCode": "insured_id_hash",
              "controlKind": "INPUT",
              "readonly": true,
              "props": {
                "mask": "hash"
              }
            },
            {
              "fieldCode": "claim_type",
              "controlKind": "ENUM"
            },
            {
              "fieldCode": "hospital_level",
              "controlKind": "ENUM"
            },
            {
              "fieldCode": "invoice_no",
              "controlKind": "INPUT"
            },
            {
              "fieldCode": "invoice_date",
              "controlKind": "DATE"
            }
          ],
          "titleKey": "sections.sec_apply"
        },
        {
          "id": "sec_items",
          "title": "费用明细",
          "controls": [
            {
              "fieldCode": "items",
              "controlKind": "CHILD_TABLE",
              "props": {
                "childEntity": "claim_item",
                "columns": [
                  "name",
                  "amount",
                  "category"
                ]
              }
            }
          ],
          "titleKey": "sections.sec_items"
        },
        {
          "id": "sec_evidences",
          "title": "材料证据",
          "controls": [
            {
              "fieldCode": "evidences",
              "controlKind": "ATTACHMENT",
              "props": {
                "maxCount": 20,
                "maxSizeMB": 10
              }
            }
          ],
          "titleKey": "sections.sec_evidences"
        },
        {
          "id": "sec_calc",
          "title": "理算引用（只读：规则引擎计算，后端为准）",
          "kind": "dashboard",
          "controls": [
            {
              "fieldCode": "deductible_used",
              "virtual": true,
              "controlKind": "STAT",
              "props": {
                "lineage": "deductible-annual-cumu"
              }
            },
            {
              "fieldCode": "payable_amount",
              "controlKind": "COMPUTED",
              "props": {
                "computedFn": "payableAmount",
                "computedFrom": [
                  "claim_amount",
                  "deductible_used"
                ],
                "lineage": "rat-cap-formula"
              }
            },
            {
              "fieldCode": "fraud_flags",
              "controlKind": "ALERT",
              "props": {
                "warningText": "命中 R-FRAUD 标记一律转人工（P0 优先级）"
              }
            }
          ],
          "titleKey": "sections.sec_calc"
        },
        {
          "id": "sec_trace",
          "title": "决策轨迹",
          "kind": "timeline",
          "controls": [
            {
              "fieldCode": "decision_trace",
              "virtual": true,
              "controlKind": "TIMELINE"
            }
          ],
          "titleKey": "sections.sec_trace"
        }
      ]
    }
  },
  "linkageRules": [
    {
      "name": "大额强制双人复核提示",
      "when": {
        "fieldCode": "claim_amount",
        "operator": "gte",
        "value": 10000
      },
      "then": [
        {
          "fieldCode": "second_reviewer",
          "visible": true,
          "required": true,
          "notice": "金额≥1 万元须双人复核（R-MAN-01）",
          "noticeKey": "notice.dual_review"
        }
      ],
      "code": "LINK-DUAL-01"
    },
    {
      "name": "材料缺失转补件提示",
      "when": {
        "fieldCode": "evidences",
        "operator": "isNull"
      },
      "then": [
        {
          "fieldCode": "evidences",
          "required": true,
          "notice": "材料缺失请补件，不阻塞其他案件（R-DEC P5）"
        }
      ],
      "code": "LINK-EVID-01"
    },
    {
      "code": "LINK-FAST-01",
      "name": "快赔资格四条件全满足才亮快赔入口（R-FAST-01，all 语义禁拆分）",
      "when": {
        "all": [
          {
            "fieldCode": "calc_amount",
            "operator": "lte",
            "value": 500
          },
          {
            "fieldCode": "evidences",
            "operator": "isNotNull"
          },
          {
            "fieldCode": "fraud_flags",
            "operator": "isNull"
          },
          {
            "fieldCode": "coverage_clear",
            "operator": "eq",
            "value": true
          }
        ]
      },
      "then": [
        {
          "fieldCode": "fasttrack_entry",
          "visible": true
        }
      ]
    }
  ],
  "submitRules": [
    {
      "name": "发票与清单一致",
      "function": "consistency",
      "fields": [
        "invoice_amount",
        "items.amount"
      ],
      "operator": "eq",
      "params": {
        "tolerance": 1
      },
      "verifyType": "ERROR",
      "message": "发票金额与清单合计不一致（差额>1 元，R-FRAUD-04）",
      "code": "SUB-CONS-01",
      "messageKey": "submit.consistency"
    },
    {
      "name": "拒赔理由完整性",
      "function": "completeness",
      "fields": [
        "reject_reasons"
      ],
      "operator": "gte",
      "params": {
        "minReasons": 3
      },
      "when": {
        "fieldCode": "status",
        "operator": "eq",
        "value": "REJECTING"
      },
      "verifyType": "ERROR",
      "message": "拒赔必须≥3 条理由+条款原文+证据位置（R-MAN-03）",
      "code": "SUB-COMP-01",
      "messageKey": "submit.reject_reasons"
    }
  ],
  "actionBar": [
    {
      "actionCode": "submit",
      "visibleWhen": {
        "fieldCode": "status",
        "operator": "eq",
        "value": "DRAFT"
      },
      "sortOrder": 1
    },
    {
      "actionCode": "approve",
      "decisionKind": "hitl",
      "visibleWhen": {
        "fieldCode": "status",
        "operator": "eq",
        "value": "REVIEWING"
      },
      "requireDualApproveWhen": {
        "fieldCode": "claim_amount",
        "operator": "gte",
        "value": 10000
      },
      "sortOrder": 2
    },
    {
      "actionCode": "reject",
      "decisionKind": "hitl",
      "visibleWhen": {
        "fieldCode": "status",
        "operator": "eq",
        "value": "REVIEWING"
      },
      "reasonTypes": [
        "责任范围不符",
        "目录外不赔",
        "已达封顶",
        "风险标记",
        "材料造假",
        "其他"
      ],
      "sortOrder": 3
    },
    {
      "actionCode": "requestMoreEvidence",
      "visibleWhen": {
        "fieldCode": "status",
        "operator": "eq",
        "value": "REVIEWING"
      },
      "sortOrder": 4
    },
    {
      "actionCode": "pay",
      "visibleWhen": {
        "fieldCode": "status",
        "operator": "eq",
        "value": "APPROVED"
      },
      "confirm": {
        "title": "确认支付",
        "content": "支付经资金两段式（硬预占→已支付），幂等键=报案号",
        "titleKey": "confirm.pay.title",
        "contentKey": "confirm.pay.content"
      },
      "sortOrder": 5
    },
    {
      "actionCode": "appeal",
      "visibleWhen": {
        "fieldCode": "status",
        "operator": "eq",
        "value": "REJECTED"
      },
      "sortOrder": 6
    }
  ],
  "footer": {
    "disclaimer": "拒赔结论附条款原文与证据位置；申诉 48h 响应",
    "aiBadge": true,
    "disclaimerKey": "footer.disclaimer"
  },
  "i18n": {
    "namespace": "bone.ui.t_claim_case"
  },
  "base": {
    "templateCode": "outpatient_fasttrack",
    "baseVersion": "1.0.0",
    "followMode": "notify"
  },
  "variants": [
    {
      "key": "reviewer",
      "label": "核赔工作台",
      "priority": 10,
      "match": {
        "roles": [
          "reviewer",
          "admin"
        ]
      },
      "views": {
        "form": {
          "sections": [
            {
              "id": "sec_calc",
              "title": "理算与风险（先看结论）",
              "kind": "dashboard",
              "controls": [
                {
                  "fieldCode": "payable_amount",
                  "controlKind": "COMPUTED",
                  "virtual": true,
                  "props": {
                    "computedFn": "payableAmount",
                    "computedFrom": [
                      "claim_amount",
                      "deductible_used"
                    ],
                    "lineage": "rat-cap-formula"
                  }
                },
                {
                  "fieldCode": "fraud_flags",
                  "controlKind": "ALERT",
                  "props": {
                    "warningText": "命中 R-FRAUD 标记一律转人工（P0 优先级）"
                  }
                }
              ]
            },
            {
              "id": "sec_trace",
              "title": "决策轨迹",
              "kind": "timeline",
              "controls": [
                {
                  "fieldCode": "decision_trace",
                  "controlKind": "TIMELINE",
                  "virtual": true
                }
              ]
            },
            {
              "id": "sec_items",
              "title": "费用明细（只读核对）",
              "controls": [
                {
                  "fieldCode": "items",
                  "controlKind": "CHILD_TABLE",
                  "readonly": true,
                  "props": {
                    "childEntity": "claim_item",
                    "columns": [
                      "name",
                      "amount",
                      "category"
                    ]
                  }
                }
              ]
            }
          ]
        },
        "detail": {
          "sections": [
            {
              "id": "sec_trace",
              "title": "决策轨迹",
              "kind": "timeline",
              "controls": [
                {
                  "fieldCode": "decision_trace",
                  "controlKind": "TIMELINE",
                  "virtual": true
                }
              ]
            }
          ]
        }
      },
      "actionBar": [
        {
          "actionCode": "approve",
          "decisionKind": "hitl",
          "visibleWhen": {
            "fieldCode": "status",
            "operator": "eq",
            "value": "REVIEWING"
          },
          "requireDualApproveWhen": {
            "fieldCode": "claim_amount",
            "operator": "gte",
            "value": 10000
          },
          "sortOrder": 1
        },
        {
          "actionCode": "reject",
          "decisionKind": "hitl",
          "visibleWhen": {
            "fieldCode": "status",
            "operator": "eq",
            "value": "REVIEWING"
          },
          "reasonTypes": [
            "责任范围不符",
            "目录外不赔",
            "已达封顶",
            "风险标记",
            "材料造假",
            "其他"
          ],
          "sortOrder": 2
        },
        {
          "actionCode": "requestMoreEvidence",
          "visibleWhen": {
            "fieldCode": "status",
            "operator": "eq",
            "value": "REVIEWING"
          },
          "sortOrder": 3
        }
      ]
    }
  ]
};

export const UI_SCHEMA_SAMPLES: UiSchemaSample[] = [
  {
    key: 'expense',
    label: '差旅报销',
    desc: '专票税号联动 + 明细合计一致 + 四级审批动作条',
    schema: SAMPLE_EXPENSE,
  },
  {
    key: 'claim',
    label: '智能理赔（门诊快赔）',
    desc: '快赔资格四条件 + 发票清单一致 + 拒赔要素完整性',
    schema: SAMPLE_CLAIM,
  },
];
