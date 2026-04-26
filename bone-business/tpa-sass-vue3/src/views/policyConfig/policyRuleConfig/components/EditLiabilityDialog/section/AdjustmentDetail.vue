<script lang="ts">
const separator = ";";
// 设置每个因素的可选值
const factorValues: Record<string, string[]> = {
  MEDICAL_INSURANCE: ["有医保", "无医保"],
  HOSPITAL_LEVEL: ["三级", "二级", "一级", "未定级"],
  HOSPITAL_TYPE: ["公立", "民营", "其他"],
};

const ALL_FACTOR = [
  {
    label: "区分医保赔付",
    value: "MEDICAL_INSURANCE",
  },
  {
    label: "区分医院等级",
    value: "HOSPITAL_LEVEL",
  },
  {
    label: "区分医院性质",
    value: "HOSPITAL_TYPE",
  },
  // {
  //   label: "区分程度比例",
  //   value: "SEVERENESS",
  // },
  {
    label: "发票总费用区间",
    value: "TOTAL_FEE",
  },
  {
    label: "案件理算金额区间",
    value: "ADJUSTMENT_FEE",
  },
  {
    label: "赔案累计区间",
    value: "CLAIM_ACCUM",
  },
  {
    label: "区分赔付次数",
    value: "PAY_TIMES",
  },
  {
    label: "区分年龄",
    value: "AGE",
  },
];

interface SpanMethodProps {
  row: any;
  column: TableColumnCtx<any>;
  rowIndex: number;
  columnIndex: number;
}
</script>

<script setup lang="ts">
import { PropType } from "vue";
import LiabilityAPI, { LiabilityDTO, AdjustmentDetail } from "@/api/liability";
import type { TableColumnCtx } from "element-plus";
defineOptions({
  name: "AdjustmentDetail",
});

const liability = defineModel("liability", {
  type: Object as PropType<LiabilityDTO>,
  required: true,
});

const liabilityType = computed(() => {
  return liability.value.liabilityType;
});

const liabilityFeeType = computed(() => {
  return (
    liability.value.restrictOutInsure.liabilityFeeType?.filter(
      (item) => item.open
    ) || []
  );
});

const payPercent = computed(() => {
  return liability.value.payPercent;
});

const liabilityLimit = computed(() => {
  if (
    liability.value.liabilityLimit.type === "PERSONAL" ||
    !liability.value.liabilityLimit.liabilityLimit
  ) {
    return undefined;
  }
  return liability.value.liabilityLimit.liabilityLimit;
});

const factors = computed(() => {
  return payPercent.value.factor;
});

const factorsWithoutMedicalInsurance = computed(() => {
  if (payPercent.value.type === "DIFFERENT") {
    return factors.value.filter((factor) => factor !== "MEDICAL_INSURANCE");
  }
  return [];
});

const isShowMedicalInsurance = computed(() => {
  return (
    payPercent.value.type === "DIFFERENT" &&
    payPercent.value.factor.includes("MEDICAL_INSURANCE")
  );
});

//是否不同比例
const isDifferentPercent = computed(() => {
  return payPercent.value.type === "DIFFERENT";
});

const getFactorLabel = (factor: string): string => {
  return ALL_FACTOR.find((item) => item.value === factor)?.label || factor;
};

const tableData = ref<any>([]);

// 转换函数：将AdjustmentDetail转换为tableData
const adjustmentDetailToTableData = () => {
  if (
    !liability.value?.adjustmentDetail ||
    !liability.value.adjustmentDetail.length
  ) {
    return [];
  }

  // 根据不同的责任类型处理
  if (liabilityType.value === "REIMBURSEMENT") {
    return adjustmentDetailToMedicalTableData();
  } else if (
    liabilityType.value === "FIXED_AMOUNT" ||
    liabilityType.value === "ALLOWANCE"
  ) {
    return adjustmentDetailToFixedTableData();
  }

  return [];
};

const adjustmentDetailToMedicalTableData = () => {
  const result: any[] = [];

  // 遍历每一项AdjustmentDetail
  liability.value.adjustmentDetail.forEach((detail) => {
    const { liabilityFeeType, hasYb, liabilityLimit, valueList } = detail;

    // 医保值转换
    const MEDICAL_INSURANCE = hasYb === "true" ? "有医保" : "无医保";

    // 遍历valueList，每一个键值对生成一行
    Object.entries(valueList).forEach(([factorKey, value]) => {
      // 构建基础行数据
      const rowData: any = {
        liabilityFeeType,
        MEDICAL_INSURANCE,
        liabilityLimit,
        value,
      };

      // 处理特殊键"DEFAULT"
      if (factorKey === "DEFAULT") {
        // 对于DEFAULT键，不需要添加额外因子
      } else {
        // 解析因子键值字符串
        const factorEntries = factorKey.split(separator).map((item) => {
          const [key, val] = item.split("=");
          return [key, val];
        });

        // 添加各个因子的值
        factorEntries.forEach(([key, val]) => {
          rowData[key] = val;
        });
      }

      result.push(rowData);
    });
  });

  return result;
};

const adjustmentDetailToFixedTableData = (): any[] => {
  const result: any[] = [];

  // 固定赔付类型只有一个AdjustmentDetail项
  const detail = liability.value.adjustmentDetail[0];
  // const liabilityLimit = detail.liabilityLimit;
  if (!detail || !detail.valueList) return [];

  // 遍历valueList，每一个键值对生成一行
  Object.entries(detail.valueList).forEach(([factorKey, value]) => {
    // 构建基础行数据
    const rowData: any = {
      value,
      // liabilityLimit,
    };

    // 处理特殊键"DEFAULT"
    if (factorKey === "DEFAULT") {
      // 对于DEFAULT键，不需要添加额外因子
    } else {
      // 解析因子键值字符串
      const factorEntries = factorKey.split(separator).map((item) => {
        const [key, val] = item.split("=");
        return [key, val];
      });

      // 添加各个因子的值
      factorEntries.forEach(([key, val]) => {
        rowData[key] = val;
      });
    }

    result.push(rowData);
  });

  return result;
};

// 转换函数：将tableData转换为AdjustmentDetail
const tableDataToAdjustmentDetail = () => {
  if (!tableData.value || !tableData.value.length) {
    return [];
  }

  // 根据不同的责任类型处理
  if (liabilityType.value === "REIMBURSEMENT") {
    return medicalTableDataToAdjustmentDetail();
  } else if (
    liabilityType.value === "FIXED_AMOUNT" ||
    liabilityType.value === "ALLOWANCE"
  ) {
    return fixedTableDataToAdjustmentDetail();
  }

  return [];
};

const medicalTableDataToAdjustmentDetail = () => {
  // 按照liabilityFeeType和MEDICAL_INSURANCE分组
  const groupedData: Record<string, any[]> = {};

  tableData.value.forEach((row: any) => {
    const key = `${row.liabilityFeeType}_${row.MEDICAL_INSURANCE}`;
    if (!groupedData[key]) {
      groupedData[key] = [];
    }
    groupedData[key].push(row);
  });

  // 转换为AdjustmentDetail格式
  const result: AdjustmentDetail[] = [];

  Object.entries(groupedData).forEach(([key, rows]) => {
    if (!rows.length) return;

    const [liabilityFeeType, medical] = key.split("_");
    const hasYb = medical === "有医保" ? "true" : "false";
    const liabilityLimit = rows[0].liabilityLimit;

    // 构建valueList
    const valueList: Record<string, number | null> = {};

    rows.forEach((row: any) => {
      // 提取所有因子键值对
      const factorPairs: string[] = [];

      // 遍历行中的所有属性，找出因子
      Object.entries(row).forEach(([key, val]) => {
        // 排除非因子字段
        if (
          key !== "liabilityFeeType" &&
          key !== "MEDICAL_INSURANCE" &&
          key !== "liabilityLimit" &&
          key !== "value"
        ) {
          factorPairs.push(`${key}=${val}`);
        }
      });

      // 生成valueList的key
      const factorKey =
        factorPairs.length > 0 ? factorPairs.join(separator) : "DEFAULT";
      valueList[factorKey] =
        row.value !== null && row.value !== undefined
          ? Number(row.value)
          : null;
    });

    // 添加到结果中
    result.push({
      liabilityFeeType,
      hasYb,
      liabilityLimit:
        liabilityLimit !== null && liabilityLimit !== undefined
          ? Number(liabilityLimit)
          : null,
      valueList,
    });
  });

  return result;
};

const fixedTableDataToAdjustmentDetail = () => {
  // 固定赔付类型只需要一个AdjustmentDetail项
  const valueList: Record<string, number | null> = {};

  // 遍历表格数据构建valueList
  tableData.value.forEach((row: any) => {
    // 提取所有因子键值对
    const factorPairs: string[] = [];

    // 遍历行中的所有属性，找出因子
    Object.entries(row).forEach(([key, val]) => {
      // 排除value字段
      if (key !== "value" && key !== "liabilityLimit") {
        factorPairs.push(`${key}=${val}`);
      }
    });

    // 生成valueList的key
    const factorKey =
      factorPairs.length > 0 ? factorPairs.join(separator) : "DEFAULT";
    valueList[factorKey] =
      row.value !== null && row.value !== undefined ? Number(row.value) : null;
  });

  // 创建AdjustmentDetail
  // const limit =
  //   tableData.value[0]?.liabilityLimit !== null &&
  //   tableData.value[0]?.liabilityLimit !== undefined
  //     ? Number(tableData.value[0]?.liabilityLimit)
  //     : null; // 固定赔付不需要
  return [
    {
      liabilityFeeType: "", // 固定赔付不需要
      hasYb: "false", // 固定赔付不需要
      liabilityLimit: null,
      valueList,
    },
  ];
};

// 判断两个表格结构是否相等（不考虑值）
const isTableStructureEqual = (table1: any[], table2: any[]): boolean => {
  if (table1.length !== table2.length) return false;

  // 按照唯一键对表格数据进行排序
  const sortTableByKey = (table: any[]) => {
    return [...table].sort((a, b) => {
      const keyA = getRowKey(a);
      const keyB = getRowKey(b);
      return keyA.localeCompare(keyB);
    });
  };

  const sortedTable1 = sortTableByKey(table1);
  const sortedTable2 = sortTableByKey(table2);

  // 比较每一行的关键结构（不包括value值）
  for (let i = 0; i < sortedTable1.length; i++) {
    const row1 = sortedTable1[i];
    const row2 = sortedTable2[i];

    // 医疗报销类型需要比较的字段
    if (liabilityType.value === "REIMBURSEMENT") {
      // 比较必须相同的字段
      if (
        row1.liabilityFeeType !== row2.liabilityFeeType ||
        ((row1.MEDICAL_INSURANCE === "有医保" ||
          row2.MEDICAL_INSURANCE === "有医保") &&
          row1.MEDICAL_INSURANCE !== row2.MEDICAL_INSURANCE)
      ) {
        return false;
      }
    }

    // 检查两行是否有相同的因子字段集合
    const getFactorFields = (row: any) => {
      // 根据不同的责任类型排除不同的字段
      const excludeFields = ["value"];
      if (liabilityType.value === "REIMBURSEMENT") {
        excludeFields.push(
          "liabilityFeeType",
          "MEDICAL_INSURANCE",
          "liabilityLimit"
        );
      }
      if (
        liabilityType.value === "FIXED_AMOUNT" ||
        liabilityType.value === "ALLOWANCE"
      ) {
        excludeFields.push("liabilityLimit");
      }

      return Object.keys(row)
        .filter((key) => !excludeFields.includes(key))
        .sort();
    };

    const factors1 = getFactorFields(row1);
    const factors2 = getFactorFields(row2);

    if (factors1.length !== factors2.length) return false;

    for (let j = 0; j < factors1.length; j++) {
      if (factors1[j] !== factors2[j]) return false;

      // 检查因子的值是否相同
      if (row1[factors1[j]] !== row2[factors2[j]]) return false;
    }
  }

  return true;
};

// 合并两个表格数据，保留现有的值
const mergeTableData = (baseTable: any[], valueTable: any[]): any[] => {
  // 复制基础表格，避免修改原始数据
  const result = JSON.parse(JSON.stringify(baseTable));

  // 创建查找映射
  const valueMap = new Map();

  // 为valueTable中的每一行创建唯一键并添加到映射中
  valueTable.forEach((row) => {
    const key = getRowKey(row);
    valueMap.set(key, row.value);
  });

  // 为结果表格中的每一行设置值
  result.forEach((row: any) => {
    const key = getRowKey(row);
    if (valueMap.has(key)) {
      row.value = valueMap.get(key);
    }
  });

  return result;
};

// 获取行的唯一键
const getRowKey = (row: any): string => {
  // 提取所有因子键值对
  const factorPairs: string[] = [];

  // 遍历行中的所有属性，找出因子
  Object.entries(row).forEach(([key, val]) => {
    // 根据不同的责任类型排除不同的字段
    if (liabilityType.value === "REIMBURSEMENT") {
      // 医疗报销类型：排除非因子字段和value字段
      if (
        key !== "liabilityFeeType" &&
        key !== "MEDICAL_INSURANCE" &&
        key !== "liabilityLimit" &&
        key !== "value"
      ) {
        factorPairs.push(`${key}=${val}`);
      }
    } else {
      // 固定赔付类型：只排除value字段
      if (key !== "value" && key !== "liabilityLimit") {
        factorPairs.push(`${key}=${val}`);
      }
    }
  });

  // 生成唯一键
  if (liabilityType.value === "REIMBURSEMENT") {
    // 医疗报销类型：liabilityFeeType_医保_因子对
    return `${row.liabilityFeeType}_${row.MEDICAL_INSURANCE}_${factorPairs.sort().join(",")}`;
  } else {
    // 固定赔付类型：只使用因子对
    return factorPairs.sort().join(",");
  }
};

// 初始化tableData
watchDebounced(
  [
    () => liabilityType.value,
    () => liabilityFeeType.value,
    () => factors.value,
    () => liabilityLimit.value,
    () => payPercent.value,
  ],
  () => {
    // 首先生成最新的基础表格数据
    const latestTableStructure = generateTableData();

    // 如果有adjustmentDetail数据，使用它初始化
    if (liability.value?.adjustmentDetail?.length) {
      const detailData = adjustmentDetailToTableData();

      // 如果表格结构没有变化，直接使用detailData
      if (isTableStructureEqual(latestTableStructure, detailData)) {
        tableData.value = detailData;
        return;
      }

      // 结构变化了，保留现有的值
      tableData.value = mergeTableData(latestTableStructure, detailData);
    } else {
      // 如果表格结构没有变化，保留现有的值
      if (isTableStructureEqual(latestTableStructure, tableData.value)) {
        return;
      }

      // 结构变化了，保留现有的值
      tableData.value = mergeTableData(latestTableStructure, tableData.value);
    }

    // 更新adjustmentDetail以保持同步
    liability.value.adjustmentDetail = tableDataToAdjustmentDetail();
  },
  { deep: true, immediate: true, debounce: 300 }
);

// 监听tableData变化，更新adjustmentDetail
watch(
  () => tableData.value,
  (newVal) => {
    if (newVal && newVal.length > 0) {
      liability.value.adjustmentDetail = tableDataToAdjustmentDetail();
    }
  },
  { deep: true }
);

const generateTableData = () => {
  if (liabilityType.value === "REIMBURSEMENT") {
    return generateMedicalTableData();
  } else if (
    liabilityType.value === "FIXED_AMOUNT" ||
    liabilityType.value === "ALLOWANCE"
  ) {
    return generateFixedTableData();
  }
  return [];
};

const generateMedicalTableData = () => {
  // 准备各因素的选项
  const factorOptions: Record<string, string[]> = {};

  factors.value.forEach((factor) => {
    // 准备固定选项值的因素
    if (factorValues[factor]) {
      factorOptions[factor] = factorValues[factor];
    } else if (
      payPercent.value &&
      payPercent.value.rangeMap &&
      payPercent.value.rangeMap[factor]
    ) {
      // 准备动态选项值的因素
      const rangeObjects = payPercent.value.rangeMap[factor] || [];
      factorOptions[factor] = rangeObjects.map((range) => {
        // 根据intervalType格式化区间
        let leftBracket =
          range.intervalType === "RIGHT_OPEN" ||
          range.intervalType === "BOTH_CLOSED"
            ? "["
            : "(";
        let rightBracket =
          range.intervalType === "LEFT_OPEN" ||
          range.intervalType === "BOTH_CLOSED"
            ? "]"
            : ")";
        return `${leftBracket}${range.lowerLimit},${range.upperLimit}${rightBracket}`;
      });
    }
  });

  // 生成笛卡尔积
  const generateCartesianProduct = (
    currentRow: any,
    remainingFactors: string[],
    result: any[]
  ) => {
    if (remainingFactors.length === 0) {
      result.push({ ...currentRow });
      return;
    }

    const currentFactor = remainingFactors[0];
    const options = factorOptions[currentFactor] || [];

    options.forEach((option) => {
      const newRow = { ...currentRow, [currentFactor]: option };
      generateCartesianProduct(newRow, remainingFactors.slice(1), result);
    });
  };

  // 生成最终表格数据
  const tableData: any = [];

  // 对每种承担费用类型生成笛卡尔积
  liabilityFeeType.value.forEach((item) => {
    const baseRow = {
      liabilityFeeType: item.feeType,
      liabilityLimit: liabilityLimit.value,
    };
    generateCartesianProduct(
      baseRow,
      isDifferentPercent.value ? factors.value : [],
      tableData
    );
  });

  return tableData;
};

// 生成固定赔付类型的表格数据
const generateFixedTableData = () => {
  // 准备各因素的选项
  const factorOptions: Record<string, string[]> = {};

  factors.value.forEach((factor) => {
    // 准备固定选项值的因素
    if (factorValues[factor]) {
      factorOptions[factor] = factorValues[factor];
    } else if (
      payPercent.value &&
      payPercent.value.rangeMap &&
      payPercent.value.rangeMap[factor]
    ) {
      // 准备动态选项值的因素
      const rangeObjects = payPercent.value.rangeMap[factor] || [];
      factorOptions[factor] = rangeObjects.map((range) => {
        // 根据intervalType格式化区间
        let leftBracket =
          range.intervalType === "RIGHT_OPEN" ||
          range.intervalType === "BOTH_CLOSED"
            ? "["
            : "(";
        let rightBracket =
          range.intervalType === "LEFT_OPEN" ||
          range.intervalType === "BOTH_CLOSED"
            ? "]"
            : ")";
        return `${leftBracket}${range.lowerLimit},${range.upperLimit}${rightBracket}`;
      });
    }
  });

  // 生成笛卡尔积
  const generateCartesianProduct = (
    currentRow: any,
    remainingFactors: string[],
    result: any[]
  ) => {
    if (remainingFactors.length === 0) {
      result.push({ ...currentRow });
      return;
    }

    const currentFactor = remainingFactors[0];
    const options = factorOptions[currentFactor] || [];

    options.forEach((option) => {
      const newRow = { ...currentRow, [currentFactor]: option };
      generateCartesianProduct(newRow, remainingFactors.slice(1), result);
    });
  };

  // 生成最终表格数据
  const tableData: any = [];

  generateCartesianProduct(
    {},
    isDifferentPercent.value ? factors.value : [],
    tableData
  );

  return tableData;
};

const spanMethod = ({
  row,
  column,
  rowIndex,
  columnIndex,
}: SpanMethodProps): [number, number] => {
  // 查找相同值的连续行数
  const findConsecutiveRows = (
    startIndex: number,
    property: string,
    limitToSameType = true
  ) => {
    const currentValue =
      property === "liabilityFeeType"
        ? tableData.value[startIndex].liabilityFeeType
        : tableData.value[startIndex][property];

    let count = 1;
    for (let i = startIndex + 1; i < tableData.value.length; i++) {
      // 如果需要限制在同一个费用类型内且已超出当前类型范围
      if (
        limitToSameType &&
        property !== "liabilityFeeType" &&
        tableData.value[i].liabilityFeeType !==
          tableData.value[startIndex].liabilityFeeType
      ) {
        break;
      }

      // 检查当前属性值是否相同
      const valueToCompare =
        property === "liabilityFeeType"
          ? tableData.value[i].liabilityFeeType
          : tableData.value[i][property];

      if (valueToCompare === currentValue) {
        count++;
      } else {
        break;
      }
    }
    return count;
  };

  // 判断是否是分组的第一行
  const isFirstRowInGroup = (index: number) => {
    return (
      index === 0 ||
      tableData.value[index].liabilityFeeType !==
        tableData.value[index - 1].liabilityFeeType
    );
  };

  // 第一列（承担费用类型列）
  if (columnIndex === 0) {
    if (
      rowIndex === 0 ||
      tableData.value[rowIndex].liabilityFeeType !==
        tableData.value[rowIndex - 1].liabilityFeeType
    ) {
      // 第一行或者不同费用类型的第一行
      const count = findConsecutiveRows(rowIndex, "liabilityFeeType");
      return [count, 1];
    } else {
      // 被上一行合并的行
      return [0, 0];
    }
  }

  // 第二列（医保列或费用限额列）
  else if (columnIndex === 1) {
    if (isShowMedicalInsurance.value) {
      // 有医保列时，第二列是医保列
      if (
        isFirstRowInGroup(rowIndex) ||
        tableData.value[rowIndex]["MEDICAL_INSURANCE"] !==
          tableData.value[rowIndex - 1]["MEDICAL_INSURANCE"]
      ) {
        // 分组第一行或者医保值不同
        const count = findConsecutiveRows(rowIndex, "MEDICAL_INSURANCE", true);
        return [count, 1];
      } else {
        // 被上一行合并
        return [0, 0];
      }
    } else {
      // 没有医保列时，第二列是费用限额列，跟随第一列合并
      return rowIndex === 0 ||
        tableData.value[rowIndex].liabilityFeeType !==
          tableData.value[rowIndex - 1].liabilityFeeType
        ? [findConsecutiveRows(rowIndex, "liabilityFeeType"), 1]
        : [0, 0];
    }
  }

  // 第三列（费用限额 只在有医保列时处理）
  else if (columnIndex === 2 && isShowMedicalInsurance.value) {
    // 复用第二列的逻辑，因为费用限额列与医保列有相同的合并规则
    return spanMethod({ row, column, rowIndex, columnIndex: 1 });
  }

  // 其他列不进行合并
  return [1, 1];
};

const fixedAmountSpanMethod = ({
  row,
  column,
  rowIndex,
  columnIndex,
}: SpanMethodProps): [number, number] => {
  // // 只有第一列为费用限额列时合并第一列
  // if (columnIndex === 0 && isNotPersonalQuato.value) {
  //   // 返回[1, 1]表示不合并，[0, 0]表示被合并
  //   return rowIndex === 0 ? [tableData.value.length, 1] : [0, 0];
  // }
  return [1, 1];
};

/**
 * 生成理算公式
 */
const generateFormula = () => {
  LiabilityAPI.generateFormula(liability.value)
    .then((res) => {
      liability.value.formula = res;
    })
    .catch((err) => {
      console.log(err);
    });
};

const validate = () => {
  if (
    !liability.value.adjustmentDetail ||
    !liability.value.adjustmentDetail.length
  ) {
    return { valid: true };
  }

  for (let detail of liability.value.adjustmentDetail) {
    if (
      liabilityType.value === "REIMBURSEMENT" &&
      (detail.liabilityLimit === null || detail.liabilityLimit === undefined)
    ) {
      return { valid: false, message: "请完整填写费用限额" };
    }
    for (let [key, value] of Object.entries(detail.valueList)) {
      if (value === null || value === undefined) {
        return { valid: false, message: "请完整填写赔付比例" };
      }
    }
  }

  return { valid: true };
};

defineExpose({
  validate,
});
</script>

<template>
  <div>
    <el-form-item label="理算公式" class="w-90%">
      <div class="flex items-center w-100%">
        <el-input class="flex-1" readonly v-model="liability.formula" />
        <el-button type="primary" class="ml-2" @click="generateFormula">
          生成理算公式
        </el-button>
      </div>
    </el-form-item>

    <div class="ml-20" v-if="liability.liabilityType === 'REIMBURSEMENT'">
      <el-form-item label-width="0px">
        <el-table border :data="tableData" :span-method="spanMethod">
          <el-table-column
            label="承担费用类型"
            align="center"
            prop="liabilityFeeType"
          />
          <el-table-column
            label="区分医保赔付"
            v-if="isShowMedicalInsurance"
            align="center"
            prop="MEDICAL_INSURANCE"
          />
          <el-table-column label="费用限额" align="center">
            <template #default="{ row }">
              <el-input-number
                v-model="row.liabilityLimit"
                style="width: 100%"
                :min="0"
                :max="liabilityLimit || undefined"
                :precision="2"
                controls-position="right"
              />
            </template>
          </el-table-column>
          <el-table-column
            v-for="item in factorsWithoutMedicalInsurance"
            :key="item"
            :label="getFactorLabel(item)"
            align="center"
            :prop="item"
          />
          <el-table-column label="赔付比例" align="center">
            <template #default="{ row }">
              <el-input-number
                v-model="row.value"
                style="width: 100%"
                :min="0"
                :max="100"
                :precision="2"
                controls-position="right"
              >
                <template #suffix>%</template>
              </el-input-number>
            </template>
          </el-table-column>
        </el-table>
      </el-form-item>
    </div>

    <div
      class="ml-20"
      v-if="
        liability.liabilityType === 'FIXED_AMOUNT' ||
        liability.liabilityType === 'ALLOWANCE'
      "
    >
      <el-form-item label-width="0px">
        <el-table border :data="tableData">
          <!-- <el-table-column
            v-if="isNotPersonalQuato"
            label="费用限额"
            align="center"
          >
            <template #default="{ row }">
              <el-input-number
                v-model="row.liabilityLimit"
                style="width: 100%"
                :min="0"
                :max="liabilityLimit || 0"
                :precision="2"
                controls-position="right"
              />
            </template>
          </el-table-column> -->
          <template v-if="payPercent.type === 'DIFFERENT'">
            <el-table-column
              v-for="item in factors"
              :key="item"
              :label="getFactorLabel(item)"
              align="center"
              :prop="item"
            />
          </template>
          <el-table-column label="赔付比例" align="center">
            <template #default="{ row }">
              <el-input-number
                v-model="row.value"
                style="width: 100%"
                :min="0"
                :max="100"
                :precision="2"
                controls-position="right"
              >
                <template #suffix>%</template>
              </el-input-number>
            </template>
          </el-table-column>
        </el-table>
      </el-form-item>
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
