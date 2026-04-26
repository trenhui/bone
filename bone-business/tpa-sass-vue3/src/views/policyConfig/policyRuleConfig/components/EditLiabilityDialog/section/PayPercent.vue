<script setup lang="ts">
import { LiabilityDTO } from "@/api/liability";
defineOptions({
  name: "PayPercent",
});

const liability = defineModel("liability", {
  type: Object as PropType<LiabilityDTO>,
  required: true,
});

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
const INTERVAL_FACTOR = [
  "TOTAL_FEE",
  "ADJUSTMENT_FEE",
  "CLAIM_ACCUM",
  "PAY_TIMES",
  "AGE",
];
const MUTUALLY_EXCLUSIVE_FACTOR = [
  "TOTAL_FEE",
  "ADJUSTMENT_FEE",
  "CLAIM_ACCUM",
];

const getFactorLabel = (factor: string): string => {
  return ALL_FACTOR.find((item) => item.value === factor)?.label || factor;
};

const isAllowMedicalInsurance = computed(() => {
  return (
    liability.value.restrictOutInsure.type.includes("HOSPITAL") &&
    liability.value.restrictOutInsure.medicalInsurance !== "BOTH"
  );
});

const showSetInterval = computed(() => {
  return (
    liability.value.payPercent.factor.length > 0 &&
    liability.value.payPercent.factor.some((item) =>
      INTERVAL_FACTOR.includes(item)
    )
  );
});

const selectedIntervalFactors = computed(() => {
  return liability.value.payPercent.factor.filter((item: any) =>
    INTERVAL_FACTOR.includes(item)
  );
});

const handleFactorChange = (value: any) => {
  //【区间比例因素】的「发票总费用」、「案件理算金额」、「赔案累计区间」，此三项互斥，最多限选一项，与其他选项可同时选择。
  //判断最后一次输入是否在互斥因素内
  const lastFactor = value[value.length - 1];
  const hasExclusiveFactor = MUTUALLY_EXCLUSIVE_FACTOR.includes(lastFactor);

  if (hasExclusiveFactor) {
    liability.value.payPercent.factor = value.filter(
      (item: any) => !MUTUALLY_EXCLUSIVE_FACTOR.includes(item)
    );
    liability.value.payPercent.factor.push(lastFactor);
  } else {
    liability.value.payPercent.factor = value;
  }

  liability.value.payPercent.factor = liability.value.payPercent.factor.sort(
    (a: string, b: string) =>
      ALL_FACTOR.findIndex((item) => item.value === a) -
      ALL_FACTOR.findIndex((item) => item.value === b)
  );
};

const addInterval = (factor: string) => {
  if (!liability.value.payPercent.rangeMap[factor]) {
    liability.value.payPercent.rangeMap[factor] = [];
  }
  liability.value.payPercent.rangeMap[factor].push({
    lowerLimit: 0,
    upperLimit: 0,
    intervalType: "BOTH_CLOSED",
    isEditing: true,
  });
};

const removeInterval = (factor: string, index: number) => {
  liability.value.payPercent.rangeMap[factor].splice(index, 1);
};

const handleAdd = (factor: string, row: any) => {
  if (
    row.lowerLimit === undefined ||
    row.upperLimit === undefined ||
    row.intervalType === undefined
  ) {
    ElMessage.warning("请填写完整后确认");
    return;
  }

  // 校验"起"必须小于"止"
  if (row.lowerLimit >= row.upperLimit) {
    ElMessage.warning("区间的起始值必须小于结束值");
    return;
  }

  // 区间之间不可重复
  const intervals = liability.value.payPercent.rangeMap[factor];

  // 检查与已有区间是否重叠
  for (let i = 0; i < intervals.length; i++) {
    const existingRow = intervals[i];

    // 跳过当前正在编辑的行
    if (existingRow.isEditing === true && existingRow === row) {
      continue;
    }

    // 确定当前区间边界点是否包含
    const isLowerIncluded =
      row.intervalType === "BOTH_CLOSED" || row.intervalType === "RIGHT_OPEN";
    const isUpperIncluded =
      row.intervalType === "BOTH_CLOSED" || row.intervalType === "LEFT_OPEN";

    // 确定已存在区间边界点是否包含
    const isExistingLowerIncluded =
      existingRow.intervalType === "BOTH_CLOSED" ||
      existingRow.intervalType === "RIGHT_OPEN";
    const isExistingUpperIncluded =
      existingRow.intervalType === "BOTH_CLOSED" ||
      existingRow.intervalType === "LEFT_OPEN";

    // 第一个条件：检查下边界
    let lowerBoundaryOverlap = false;
    if (row.lowerLimit === existingRow.upperLimit) {
      // 当下边界等于已存在区间的上边界时，只有两个边界都是闭区间才算重叠
      lowerBoundaryOverlap = isLowerIncluded && isExistingUpperIncluded;
    } else {
      lowerBoundaryOverlap = row.lowerLimit < existingRow.upperLimit;
    }

    // 第二个条件：检查上边界
    let upperBoundaryOverlap = false;
    if (row.upperLimit === existingRow.lowerLimit) {
      // 当上边界等于已存在区间的下边界时，只有两个边界都是闭区间才算重叠
      upperBoundaryOverlap = isUpperIncluded && isExistingLowerIncluded;
    } else {
      upperBoundaryOverlap = row.upperLimit > existingRow.lowerLimit;
    }

    // 两个条件都满足才算重叠
    if (lowerBoundaryOverlap && upperBoundaryOverlap) {
      ElMessage.warning("新增区间与已有区间重叠，请修改");
      return;
    }
  }

  row.isEditing = false;
};

const handleCancel = (factor: string, index: number) => {
  liability.value.payPercent.rangeMap[factor].splice(index, 1);
};

const isNotREIMBURSEMENT = computed(() => {
  return liability.value.liabilityType !== "REIMBURSEMENT";
});
</script>

<template>
  <div>
    <el-form-item label="赔付比例">
      <el-radio-group v-model="liability.payPercent.type">
        <el-radio value="SAME">同一比例</el-radio>
        <el-radio value="DIFFERENT" :disabled="isNotREIMBURSEMENT">
          不同比例
        </el-radio>
      </el-radio-group>
    </el-form-item>

    <div class="ml-20" v-if="liability.payPercent.type === 'SAME'">
      <el-form-item prop="payPercent.percent" label="赔付比例" class="w-40%">
        <el-input-number
          v-model.number="liability.payPercent.percent"
          placeholder="请输入赔付比例"
          :min="0"
          :max="100"
          controls-position="right"
        >
          <template #suffix>%</template>
        </el-input-number>
      </el-form-item>
    </div>

    <div class="ml-20" v-if="liability.payPercent.type === 'DIFFERENT'">
      <el-form-item label="不同比例因素" label-width="100px">
        <el-checkbox-group
          v-model="liability.payPercent.factor"
          @change="handleFactorChange"
        >
          <el-checkbox
            value="MEDICAL_INSURANCE"
            :disabled="isAllowMedicalInsurance"
          >
            区分医保赔付
          </el-checkbox>
          <el-checkbox value="HOSPITAL_LEVEL">区分医院等级</el-checkbox>
          <el-checkbox value="HOSPITAL_TYPE">区分医院性质</el-checkbox>
          <!-- <el-checkbox value="SEVERENESS">区分程度比例</el-checkbox> -->
        </el-checkbox-group>
      </el-form-item>

      <el-form-item label="区间赔付因素" label-width="100px">
        <el-checkbox-group
          v-model="liability.payPercent.factor"
          @change="handleFactorChange"
        >
          <el-checkbox value="CLAIM_ACCUM">赔案累计区间</el-checkbox>
          <el-checkbox value="TOTAL_FEE" disabled>发票总费用区间</el-checkbox>
          <el-checkbox value="ADJUSTMENT_FEE" disabled>
            案件理算金额区间
          </el-checkbox>
          <el-checkbox value="PAY_TIMES" disabled>区分赔付次数</el-checkbox>
          <el-checkbox value="AGE" disabled>区分年龄</el-checkbox>
        </el-checkbox-group>
      </el-form-item>

      <el-form-item class="w-60%" label-width="0px" v-if="showSetInterval">
        <div>设置区间</div>

        <template v-for="item in selectedIntervalFactors" :key="item">
          <el-table
            border
            class="my-2"
            :data="liability.payPercent.rangeMap?.[item]"
          >
            <el-table-column :label="`${getFactorLabel(item)}起`">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.lowerLimit"
                  placeholder="请输入起始值"
                  style="width: 100%"
                  :min="0"
                  controls-position="right"
                  :disabled="!row.isEditing"
                >
                  <template #append>%</template>
                </el-input-number>
              </template>
            </el-table-column>
            <el-table-column :label="`${getFactorLabel(item)}止`">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.upperLimit"
                  placeholder="请输入终止值"
                  style="width: 100%"
                  controls-position="right"
                  :disabled="!row.isEditing"
                >
                  <template #append>%</template>
                </el-input-number>
              </template>
            </el-table-column>
            <el-table-column label="起止类型" align="center">
              <template #default="{ row }">
                <el-select
                  v-model="row.intervalType"
                  placeholder="请选择"
                  :disabled="!row.isEditing"
                >
                  <el-option label="左开右闭" value="LEFT_OPEN" />
                  <el-option label="左闭右开" value="RIGHT_OPEN" />
                  <el-option label="左开右开" value="BOTH_OPEN" />
                  <el-option label="左闭右闭" value="BOTH_CLOSED" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="操作" align="center">
              <template #default="scope">
                <template v-if="!scope.row.isEditing">
                  <el-button
                    type="danger"
                    link
                    icon="Delete"
                    @click="removeInterval(item, scope.$index)"
                  >
                    删除
                  </el-button>
                </template>

                <template v-else>
                  <el-button
                    type="success"
                    icon="Check"
                    link
                    @click="handleAdd(item, scope.row)"
                  >
                    确认
                  </el-button>
                  <el-button
                    type="warning"
                    icon="Close"
                    link
                    @click="handleCancel(item, scope.$index)"
                  >
                    取消
                  </el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <el-button
            type="primary"
            link
            class="mb-2"
            @click="addInterval(item)"
          >
            新增区间
          </el-button>
        </template>
      </el-form-item>
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
