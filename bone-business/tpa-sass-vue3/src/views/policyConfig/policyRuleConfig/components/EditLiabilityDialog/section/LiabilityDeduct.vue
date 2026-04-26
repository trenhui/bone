<script lang="ts">
// 设置因素选项
const factorOptions = [
  { label: "区分是否社保", value: "SOCIAL_SECURITY" },
  { label: "区分医院等级", value: "HOSPITAL_LEVEL" },
  { label: "区分医院性质", value: "HOSPITAL_TYPE" },
  { label: "区分费用类型", value: "LIABILITY_FEE" },
];

// 设置每个因素的可选值
const factorValues = ref<Record<string, { label: string; value: string }[]>>({
  SOCIAL_SECURITY: [
    { label: "有医保", value: "有医保" },
    { label: "无医保", value: "无医保" },
  ],
  HOSPITAL_LEVEL: [
    { label: "三级", value: "三级" },
    { label: "二级", value: "二级" },
    { label: "一级", value: "一级" },
    { label: "未定级", value: "未定级" },
  ],
  HOSPITAL_TYPE: [
    { label: "公立", value: "公立" },
    { label: "民营", value: "民营" },
    { label: "其他", value: "其他" },
  ],
  LIABILITY_FEE: [],
});
const separator = ";";
</script>

<script setup lang="ts">
import { LiabilityDTO, LiabilityDeduct } from "@/api/liability";
import { PropType } from "vue";
defineOptions({
  name: "LiabilityDeduct",
});

const liability = defineModel("liability", {
  type: Object as PropType<LiabilityDTO>,
  required: true,
});

const initDeduct = () => {
  return {
    deductPattern: "",
    deductType: "",
    deductMode: "MONEY",
    deductPeriod: "",
    deductTarget: "",
    deductRule: "",
    factor: [],
    valueList: {},
  } as LiabilityDeduct;
};

const isDeductible = ref(false);
const deductData = ref<any>([{ value: null }]);

const deductPattern = computed({
  get() {
    return liability.value?.liabilityDeduct?.deductPattern || "";
  },
  set(newValue) {
    if (!liability.value?.liabilityDeduct) {
      liability.value.liabilityDeduct = initDeduct();
      liability.value.liabilityDeduct.deductPattern = newValue || "";
    } else {
      liability.value.liabilityDeduct.deductPattern = newValue || "";
    }
  },
});

const deductType = computed({
  get() {
    return liability.value?.liabilityDeduct?.deductType || "";
  },
  set(newValue) {
    if (!liability.value?.liabilityDeduct) {
      liability.value.liabilityDeduct = initDeduct();
      liability.value.liabilityDeduct.deductType = newValue || "";
    } else {
      liability.value.liabilityDeduct.deductType = newValue || "";
    }
  },
});

const deductMode = computed({
  get() {
    return liability.value?.liabilityDeduct?.deductMode || "";
  },
  set(newValue) {
    if (!liability.value?.liabilityDeduct) {
      liability.value.liabilityDeduct = initDeduct();
      liability.value.liabilityDeduct.deductMode = newValue || "";
    } else {
      liability.value.liabilityDeduct.deductMode = newValue || "";
    }
  },
});

const deductPeriod = computed({
  get() {
    return liability.value?.liabilityDeduct?.deductPeriod || "";
  },
  set(newValue) {
    if (!liability.value?.liabilityDeduct) {
      liability.value.liabilityDeduct = initDeduct();
      liability.value.liabilityDeduct.deductPeriod = newValue || "";
    } else {
      liability.value.liabilityDeduct.deductPeriod = newValue || "";
    }
  },
});

const deductTarget = computed({
  get() {
    return liability.value?.liabilityDeduct?.deductTarget || "";
  },
  set(newValue) {
    if (!liability.value?.liabilityDeduct) {
      liability.value.liabilityDeduct = initDeduct();
      liability.value.liabilityDeduct.deductTarget = newValue || "";
    } else {
      liability.value.liabilityDeduct.deductTarget = newValue || "";
    }
  },
});

const deductRule = computed({
  get() {
    return liability.value?.liabilityDeduct?.deductRule || "";
  },
  set(newValue) {
    if (!liability.value?.liabilityDeduct) {
      liability.value.liabilityDeduct = initDeduct();
      liability.value.liabilityDeduct.deductRule = newValue || "";
    } else {
      liability.value.liabilityDeduct.deductRule = newValue || "";
    }
  },
});

const factor = computed({
  get() {
    return liability.value?.liabilityDeduct?.factor || [];
  },
  set(newValue) {
    if (!liability.value?.liabilityDeduct) {
      liability.value.liabilityDeduct = initDeduct();
      liability.value.liabilityDeduct.factor = newValue || [];
    } else {
      liability.value.liabilityDeduct.factor = newValue || [];
    }
  },
});

// 用于界面显示的 factor，将空数组转换为 ["NONE"]，将 ["NONE"] 转换为空数组
const displayFactor = computed({
  get() {
    const currentFactor = factor.value;
    // 如果 factor 为空数组或只包含空值，界面上显示 "NONE"
    if (!currentFactor || currentFactor.length === 0) {
      return ["NONE"];
    }
    // 过滤掉 "NONE"，因为实际的 factor 中不应该包含 "NONE"
    return currentFactor.filter((item) => item !== "NONE");
  },
  set(newValue) {
    // 这个 setter 不会被直接调用，因为我们使用 handleFactorChange
  },
});

// valueList 转 deductData
const valueListToDeductData = () => {
  if (!liability.value?.liabilityDeduct?.valueList) {
    return [{ value: null }];
  }

  const valueList = liability.value.liabilityDeduct.valueList;

  // 如果只有DEFAULT键，表示没有区分免赔
  if (
    Object.keys(valueList).length === 1 &&
    valueList["DEFAULT"] !== undefined
  ) {
    return [{ value: valueList["DEFAULT"] }];
  }

  // 处理有区分免赔的情况
  return Object.entries(valueList).map(([key, value]) => {
    // 如果是DEFAULT键，返回简单对象
    if (key === "DEFAULT") {
      return { value };
    }

    // 解析复合键，例如: "HOSPITAL_LEVEL=一级,HOSPITAL_TYPE=公立"
    const result: Record<string, any> = { value };
    const pairs = key.split(separator);

    pairs.forEach((pair) => {
      const [factorKey, factorValue] = pair.split("=");
      result[factorKey] = factorValue;
    });

    return result;
  });
};

// deductData 转 valueList
const deductDataToValueList = () => {
  const result: Record<string, number> = {};

  // 如果没有区分免赔
  if (!factor.value || factor.value.length === 0) {
    // 只取第一个元素的value
    if (deductData.value.length > 0 && deductData.value[0].value !== "") {
      result["DEFAULT"] = deductData.value[0].value;
    }
    return result;
  }

  // 处理有区分免赔的情况
  deductData.value.forEach((item: Record<string, any>) => {
    // 构建key
    const pairs = factor.value.map((f) => `${f}=${item[f]}`);
    const key = pairs.join(separator);

    result[key] = item.value;
  });

  return result;
};

// 根据选中的因素生成笛卡尔积
const generateCombinations = (factors: string[]) => {
  if (factors.length === 0) {
    return [{ value: null }];
  }

  let result: any[] = [{}];

  // 遍历所有选中的因素
  factors.forEach((factor) => {
    // 如果该因素没有预定义的值或为空数组，跳过
    if (
      !factorValues.value[factor] ||
      factorValues.value[factor].length === 0
    ) {
      return;
    }

    // 创建新的结果数组
    const newResult: any[] = [];

    // 对当前结果中的每个对象，与当前因素的每个可能值组合
    result.forEach((obj) => {
      factorValues.value[factor].forEach((fv) => {
        // 创建新对象，包含原对象的所有属性，以及当前因素的新值
        newResult.push({
          ...obj,
          [factor]: fv.value,
          value: obj.value !== undefined ? obj.value : null,
        });
      });
    });

    // 更新结果
    result = newResult;
  });

  return result;
};

// 添加标记防止循环更新
const isUpdatingFromValueList = ref(false);
const isUpdatingFromDeductData = ref(false);

watch(
  deductData,
  () => {
    if (isUpdatingFromValueList.value) {
      console.log("跳过从deductData到valueList的更新");
      return; // 如果是从 valueList 更新来的，不再触发更新 valueList
    }

    if (!isDeductible.value) {
      console.log("免赔未启用，跳过更新");
      return;
    }

    console.log("从deductData更新到valueList");
    isUpdatingFromDeductData.value = true;

    // 使用nextTick延迟执行，让Vue完成当前的渲染周期
    nextTick(() => {
      if (!liability.value?.liabilityDeduct) {
        liability.value.liabilityDeduct = initDeduct();
      }
      liability.value.liabilityDeduct.valueList = deductDataToValueList();

      // 使用setTimeout确保标记重置发生在所有可能的更新之后
      setTimeout(() => {
        isUpdatingFromDeductData.value = false;
        console.log("更新标记重置");
      }, 0);
    });
  },
  { deep: true }
);

watchDebounced(
  [
    factor,
    () => liability.value?.restrictOutInsure?.liabilityFeeType,
    () => liability.value?.liabilityDeduct?.valueList,
  ],
  ([newFactor, newFeeType, newValueList]) => {
    if (isUpdatingFromDeductData.value) {
      console.log("跳过从valueList到deductData的更新");
      return; // 如果是从 deductData 更新来的，不再触发更新 deductData
    }

    console.log("从valueList更新到deductData");

    // 设置标记，防止循环更新
    isUpdatingFromValueList.value = true;

    try {
      // 更新 LIABILITY_FEE 的可选值
      factorValues.value["LIABILITY_FEE"] =
        newFeeType
          ?.filter((item) => item.open)
          ?.map((item) => ({
            label: item.feeType,
            value: item.feeType,
          })) || [];

      const newDeductData: any[] = [];
      // 重新生成组合
      if (!newFactor || newFactor.length === 0) {
        newDeductData.push({ value: null });
      } else {
        newDeductData.push(...generateCombinations(newFactor));
      }

      // 合并现有数据和新生成的组合
      if (newValueList && Object.keys(newValueList).length > 0) {
        console.log("合并已有 valueList 数据到新组合", newValueList);

        // 遍历所有新组合
        newDeductData.forEach((combination) => {
          // 构建此组合对应的 valueList 键
          let valueListKey = "DEFAULT";

          if (newFactor && newFactor.length > 0) {
            const pairs = newFactor.map((f) => `${f}=${combination[f]}`);
            valueListKey = pairs.join(separator);
          }

          // 如果 valueList 中有对应的数据，使用它
          if (newValueList[valueListKey] !== undefined) {
            combination.value = newValueList[valueListKey];
          }
        });
      }

      // 更新 deductData
      nextTick(() => {
        deductData.value = newDeductData;
      });
    } finally {
      // 确保标记重置发生在所有可能的更新之后
      setTimeout(() => {
        isUpdatingFromValueList.value = false;
        console.log("更新标记重置");
      }, 50); // 稍微增加延迟确保所有更新完成
    }
  },
  { immediate: true, deep: true, debounce: 300 }
);

watch(
  [
    () => liability.value?.liabilityType,
    () => liability.value?.liabilityDeduct,
  ],
  ([newType, newDeduct]) => {
    isDeductible.value = newDeduct !== null;
    if (newType === "ALLOWANCE" && isDeductible.value) {
      deductMode.value = "DAYS";
    }
  },
  { immediate: true, deep: true }
);

// 设置表格和表格列的宽度
const defaultWidth = 200;
const tableMinWidth = computed(() => {
  const validFactor = factor.value.filter((item) => {
    return factorValues.value[item] && factorValues.value[item].length > 0;
  });
  return ((validFactor.length ?? 0) + 1) * defaultWidth;
});

const deductModeLabel = computed(() => {
  switch (deductMode.value) {
    case "MONEY":
      return "免赔金额";
    case "RATIO":
      return "免赔比例";
    case "DAYS":
      return "免赔天数";
    default:
      return "";
  }
});

const getFactorLabel = (factor: string) => {
  return factorOptions.find((item) => item.value === factor)?.label || factor;
};

const handleIsDeductibleChange = (value: any) => {
  isDeductible.value = value;
  if (!value) {
    liability.value.liabilityDeduct = null;
  }
};

const handleFactorChange = (value: any) => {
  // 如果选择了 "NONE"（否），将 factor 设置为空数组
  if (value.length > 0 && value[value.length - 1] === "NONE") {
    factor.value = [];
    return;
  }

  // 过滤掉 "NONE"（如果用户先选了其他选项再选择否，然后又取消否）
  const filteredValue = value.filter((item: string) => item !== "NONE");

  // 按照 factorOptions 的顺序排序
  factor.value = filteredValue.sort(
    (a: string, b: string) =>
      factorOptions.findIndex((item) => item.value === a) -
      factorOptions.findIndex((item) => item.value === b)
  );
};

const handleDeductModeChange = (value: any) => {
  deductMode.value = value;
  // 免赔比例只能选按次
  if (value === "RATIO") {
    deductPeriod.value = "TIMES";
  }
};

const handleDeductTypeChange = (value: any) => {
  deductType.value = value;
  // 相对免赔不能选免赔比例
  if (value === "RELATIVE" && deductMode.value === "RATIO") {
    deductMode.value = "";
  }
};

// 校验函数：检查所有免赔额是否已填写
const validate = () => {
  if (!isDeductible.value) {
    // 如果未启用免赔，无需校验
    return { valid: true };
  }

  // 检查是否所有的 value 都已填写
  const emptyItems: number[] = [];
  deductData.value.forEach((item: any, index: number) => {
    if (item.value === null || item.value === undefined || item.value === "") {
      emptyItems.push(index + 1);
    }
  });

  if (emptyItems.length > 0) {
    return {
      valid: false,
      message: `请填写完整的免赔数据，第 ${emptyItems.join("、")} 行的${deductModeLabel.value}未填写`,
    };
  }

  return { valid: true };
};

// 导出校验函数供父组件使用
defineExpose({
  validate,
});
</script>

<template>
  <div>
    <el-form-item label="责任免赔">
      <el-radio-group v-model="isDeductible" @change="handleIsDeductibleChange">
        <el-radio :value="false">无免赔</el-radio>
        <el-radio :value="true">启用免赔</el-radio>
      </el-radio-group>
    </el-form-item>

    <!-- 启用免赔时显示 -->
    <div class="ml-20" v-if="isDeductible">
      <el-form-item label-width="0px">
        <div>设置免赔规则</div>
        <el-table border :data="[0]">
          <el-table-column label="免赔模式" align="center">
            <template #default>
              <el-select v-model="deductPattern">
                <el-option label="统一额度" value="UNIVERSAL" />
                <el-option label="个人免赔额度" value="PERSONAL" />
                <el-option label="直付免赔额度" value="DIRECT" />
              </el-select>
            </template>
          </el-table-column>
          <!-- <el-table-column label="免赔类型" align="center">
            <template #default>
              <el-select v-model="deductType" @change="handleDeductTypeChange">
                <el-option label="绝对免赔" value="ABSOLUTE" />
                <el-option label="相对免赔" value="RELATIVE" />
              </el-select>
            </template>
          </el-table-column> -->
          <el-table-column label="免赔形式" align="center">
            <template #default>
              <el-select
                v-model="deductMode"
                @change="handleDeductModeChange"
                :disabled="liability.liabilityType === 'ALLOWANCE'"
              >
                <el-option label="免赔金额" value="MONEY" />
                <el-option
                  :disabled="deductType === 'RELATIVE'"
                  label="免赔比例"
                  value="RATIO"
                />
                <el-option label="免赔天数" value="DAYS" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="免赔方式" align="center">
            <template #default>
              <el-select v-model="deductPeriod">
                <el-option
                  :disabled="deductMode === 'RATIO'"
                  label="按年度"
                  value="ANNUAL"
                />
                <el-option label="按次数" value="TIMES" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="免赔对象" align="center">
            <template #default>
              <el-select v-model="deductTarget">
                <el-option label="理算金额" value="ADJUST" />
                <el-option label="发票费用" value="INVOICE" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="免赔抵扣" align="center">
            <template #default>
              <el-select v-model="deductRule">
                <el-option
                  label="医保及三方赔付可抵扣免赔"
                  value="MEDICAL_THIRD"
                />
                <el-option label="医保及三方赔付不可抵扣免赔" value="THIRD" />
                <el-option
                  label="医保不可抵扣但三方赔付可抵扣免赔"
                  value="NONE"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="是否不同免赔" align="center">
            <template #default>
              <el-select
                v-model="displayFactor"
                multiple
                :multiple-limit="2"
                collapse-tags
                collapse-tags-tooltip
                @change="handleFactorChange"
              >
                <el-option label="否" value="NONE" />
                <el-option
                  v-for="item in factorOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
      </el-form-item>

      <el-form-item label-width="0px">
        <div>
          <div>设置免赔数据</div>
          <div :style="{ width: tableMinWidth + 'px', maxWidth: '100%' }">
            <el-table border :data="deductData">
              <template v-for="item in factor" :key="item">
                <el-table-column
                  v-if="factorValues[item] && factorValues[item].length > 0"
                  :label="getFactorLabel(item)"
                  align="center"
                >
                  <template #default="{ row }">
                    {{ row[item] }}
                  </template>
                </el-table-column>
              </template>

              <el-table-column :label="deductModeLabel" align="center">
                <template #default="{ row }">
                  <el-input
                    type="number"
                    v-model.number="row.value"
                    placeholder="请输入"
                    style="width: 100%"
                  >
                    <template v-if="deductMode === 'RATIO'" #suffix>%</template>
                  </el-input>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </el-form-item>
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
