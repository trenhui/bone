<script lang="ts">
const defaultInvoiceFeeType = [
  {
    feeType: "发票总费用",
    open: true,
    source: "INPUT",
    calculateFormula: "",
    isDefault: true,
  },
  {
    feeType: "基金总支付",
    open: true,
    source: "INPUT",
    calculateFormula: "",
    isDefault: true,
    isExpanded: false,
  },
  {
    feeType: "基本统筹金额",
    open: false,
    source: "INPUT",
    calculateFormula: "",
    isDefault: true,
    parentFeeType: "基金总支付",
  },
  {
    feeType: "其他基金金额",
    open: false,
    source: "INPUT",
    calculateFormula: "",
    isDefault: true,
    parentFeeType: "基金总支付",
  },
  {
    feeType: "统筹起付线",
    open: false,
    source: "INPUT",
    calculateFormula: "",
    isDefault: true,
  },
  {
    feeType: "统筹赔付比例",
    open: false,
    source: "INPUT",
    calculateFormula: "",
    isDefault: true,
  },
  {
    feeType: "自付一",
    open: false,
    source: "CALCULATE",
    calculateFormula:
      "自付一=统筹起付线+(发票总费用-统筹起付线-自付二-总自费)*(1-统筹赔付比例)",
    isDefault: true,
  },
  {
    feeType: "自付二",
    open: true,
    source: "INPUT",
    calculateFormula: "",
    isDefault: true,
  },
  {
    feeType: "总自费",
    open: true,
    source: "INPUT",
    calculateFormula: "",
    isDefault: true,
    isExpanded: false,
  },
  {
    feeType: "丙类自费",
    open: true,
    source: "INPUT",
    calculateFormula: "",
    isDefault: true,
    parentFeeType: "总自费",
  },
  {
    feeType: "超限价自付",
    open: true,
    source: "INPUT",
    calculateFormula: "",
    isDefault: true,
    parentFeeType: "总自费",
  },
  {
    feeType: "三方已赔",
    open: true,
    source: "INPUT",
    calculateFormula: "",
    isDefault: true,
  },
  {
    feeType: "不合理金额",
    open: true,
    source: "INPUT",
    calculateFormula: "",
    isDefault: true,
  },
];

const defaultLiabilityFeeType = [
  {
    feeType: "总自费",
    open: false,
    source: "INPUT",
    calculateFormula: "",
    isDefault: true,
    isExpanded: false,
  },
  {
    feeType: "丙类自费",
    open: false,
    source: "INPUT",
    calculateFormula: "",
    isDefault: true,
    parentFeeType: "总自费",
  },
  {
    feeType: "超限价自付",
    open: false,
    source: "INPUT",
    calculateFormula: "",
    isDefault: true,
    parentFeeType: "总自费",
  },
  {
    feeType: "自付二",
    open: false,
    source: "INPUT",
    calculateFormula: "",
    isDefault: true,
  },
  {
    feeType: "合理金额",
    open: false,
    source: "CALCULATE",
    calculateFormula:
      "合理金额=发票总费用-基金总支付-自付二-总自费-三方已赔-不合理金额",
    isDefault: true,
  },
];
</script>

<script setup lang="ts">
import { LiabilityDTO, InvoiceFeeType } from "@/api/liability";
import SetExpressionDialog from "../../SetExpressionDialog/index.vue";
import { isEmpty } from "lodash-es";
defineOptions({
  name: "RestricOutInsure",
});

const liability = defineModel("liability", {
  type: Object as PropType<LiabilityDTO>,
  required: true,
});

const medicalTypeOptions = [
  { label: "门/急诊", value: "OUTPATIENT_EMERGENCY" },
  { label: "住院", value: "INPATIENT" },
  { label: "药房", value: "PHARMACY" },
  { label: "门诊慢特病", value: "SPECIAL_CLINIC" },
  { label: "其他", value: "OTHER" },
];

const accidentTypeOptions = [
  { label: "意外身故", value: "ACCIDENTAL_DEATH" },
  { label: "意外伤残", value: "ACCIDENTAL_DISABILITY" },
  { label: "意外医疗", value: "ACCIDENTAL_MEDICAL" },
];

const handleAddInvoiceFeeType = () => {
  if (isEmpty(liability.value.restrictOutInsure.invoiceFeeType)) {
    ElMessage.error("请先设置发票费用类型");
    return;
  }
  //插到最后一个parentFeeType===基金总支付的后面，如果没有parentFeeType===基金总支付的，插到基金总支付的后面
  const lastIndex =
    liability.value.restrictOutInsure.invoiceFeeType?.findLastIndex(
      (item) => item.parentFeeType === "基金总支付"
    ) ??
    liability.value.restrictOutInsure.invoiceFeeType?.findIndex(
      (item) => item.parentFeeType === "基金总支付"
    ) ??
    -1;

  if (lastIndex !== -1) {
    liability.value.restrictOutInsure.invoiceFeeType?.splice(lastIndex + 1, 0, {
      feeType: "",
      open: true,
      source: "INPUT",
      calculateFormula: "",
      isDefault: false,
      parentFeeType: "基金总支付",
    });
  }
};

const handleRemoveInvoiceFeeType = (row: InvoiceFeeType) => {
  ElMessageBox.confirm(`是否删除发票金额类型【${row.feeType}】`, "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    center: true,
  })
    .then(() => {
      //从invoiceFeeType中删除
      liability.value.restrictOutInsure.invoiceFeeType =
        liability.value.restrictOutInsure.invoiceFeeType?.filter(
          (item) => item !== row
        );
    })
    .catch(() => {});
};

const handleCreateLiabilityFeeType = () => {
  if (isEmpty(liability.value.restrictOutInsure.liabilityFeeType)) {
    liability.value.restrictOutInsure.liabilityFeeType = [];
  }
  liability.value.restrictOutInsure.liabilityFeeType?.push({
    feeType: "",
    open: false,
    source: "",
    calculateFormula: "",
    isDefault: false,
  });
};

const handleRemoveLiabilityFeeType = (row: InvoiceFeeType) => {
  ElMessageBox.confirm(`是否删除承担费用类型【${row.feeType}】`, "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    center: true,
  })
    .then(() => {
      liability.value.restrictOutInsure.liabilityFeeType =
        liability.value.restrictOutInsure.liabilityFeeType?.filter(
          (item) => item !== row
        );
    })
    .catch(() => {});
};

const setExpressionDialog = ref<{
  visible: boolean;
  params: {
    invoiceFeeTypes: InvoiceFeeType[];
    oldExpression?: string;
    name: string;
  };
  onClose: () => void;
  onConfirm: (expression: string) => void;
}>({
  visible: false,
  params: {
    invoiceFeeTypes: [],
    oldExpression: "",
    name: "",
  },
  onClose: () => {
    setExpressionDialog.value.visible = false;
    setExpressionDialog.value.params.invoiceFeeTypes = [];
    setExpressionDialog.value.params.oldExpression = "";
    setExpressionDialog.value.params.name = "";
  },
  onConfirm: (expression: string) => {},
});

const setExpression = (row: InvoiceFeeType) => {
  if (
    liability.value.restrictOutInsure.invoiceFeeType &&
    liability.value.restrictOutInsure.invoiceFeeType.length > 0
  ) {
    setExpressionDialog.value.visible = true;
    setExpressionDialog.value.params.invoiceFeeTypes =
      liability.value.restrictOutInsure.invoiceFeeType;
    if (row.calculateFormula) {
      setExpressionDialog.value.params.oldExpression = row.calculateFormula;
    }
    setExpressionDialog.value.params.name = row.feeType;
    setExpressionDialog.value.onConfirm = (expression: string) => {
      row.calculateFormula = expression;
    };
  } else {
    ElMessage.error("请先设置发票费用类型");
  }
};

onMounted(() => {
  if (isEmpty(liability.value.restrictOutInsure.liabilityFeeType)) {
    liability.value.restrictOutInsure.liabilityFeeType =
      defaultLiabilityFeeType;
  }
  if (isEmpty(liability.value.restrictOutInsure.invoiceFeeType)) {
    liability.value.restrictOutInsure.invoiceFeeType = defaultInvoiceFeeType;
  }
});

const filteredInvoiceFeeTypes = computed(() => {
  return (
    liability.value.restrictOutInsure.invoiceFeeType?.filter(
      (item) =>
        !item.parentFeeType || // 没有父类型的项目
        liability.value.restrictOutInsure.invoiceFeeType?.find(
          (feeType) => feeType.feeType === item.parentFeeType
        )?.isExpanded
    ) || []
  );
});

const hasChildInvoiceFeeTypes = (feeType: string) => {
  return (
    liability.value.restrictOutInsure.invoiceFeeType?.some(
      (item) => item.parentFeeType === feeType
    ) || false
  );
};

const filteredLiabilityFeeTypes = computed(() => {
  return (
    liability.value.restrictOutInsure.liabilityFeeType?.filter(
      (item) =>
        !item.parentFeeType || // 没有父类型的项目
        liability.value.restrictOutInsure.liabilityFeeType?.find(
          (feeType) => feeType.feeType === item.parentFeeType
        )?.isExpanded
    ) || []
  );
});

const hasChildLiabilityFeeTypes = (feeType: string) => {
  return (
    liability.value.restrictOutInsure.liabilityFeeType?.some(
      (item) => item.parentFeeType === feeType
    ) || false
  );
};

const handleLiabilityFeeTypeExpand = (row: InvoiceFeeType) => {
  row.isExpanded = !row.isExpanded;
  const childs = liability.value.restrictOutInsure.liabilityFeeType?.filter(
    (item) => item.parentFeeType === row.feeType
  );
  if (row.feeType === "总自费" && row.isExpanded) {
    row.open = false;
    row.source = "CALCULATE";
    row.calculateFormula = `${row.feeType}=${childs?.map((item) => item.feeType)?.join("+") || ""}`;

    childs?.forEach((item) => {
      item.open = true;
    });
  } else if (row.feeType === "总自费" && !row.isExpanded) {
    row.open = true;
    row.source = "INPUT";
    row.calculateFormula = "";

    childs?.forEach((item) => {
      item.open = false;
    });
  }
};

watch(
  () => liability.value.restrictOutInsure.liabilityFeeType,
  (newVal) => {
    // 如果总自费是不承担的则展开
    const feeType = newVal?.find((item) => item.feeType === "总自费");
    if (feeType && !feeType.open && !feeType.isExpanded) {
      feeType.isExpanded = true;
    }
  },
  { deep: true }
);

const isTypeREIMBURSEMENT = computed(() => {
  return liability.value.liabilityType === "REIMBURSEMENT";
});

//选择费用报销型时，自动添加医疗
watch(
  isTypeREIMBURSEMENT,
  (newVal) => {
    if (
      newVal &&
      !liability.value.restrictOutInsure.type.includes("HOSPITAL")
    ) {
      liability.value.restrictOutInsure.type.push("HOSPITAL");
    }
  },
  { immediate: true }
);

const validate = () => {
  // 检查feeType是否都有值
  if (!isEmpty(liability.value.restrictOutInsure.invoiceFeeType)) {
    const result = liability.value.restrictOutInsure.invoiceFeeType?.every(
      (item) => item.feeType
    );
    if (!result) {
      return { valid: false, message: "请检查发票费用类型名称是否填写完整" };
    }
  }

  if (!isEmpty(liability.value.restrictOutInsure.liabilityFeeType)) {
    const result = liability.value.restrictOutInsure.liabilityFeeType?.every(
      (item) => item.feeType
    );
    if (!result) {
      return { valid: false, message: "请检查承担费用类型名称是否填写完整" };
    }
  }

  // 如果选择费用报销型，则承担费用类型有至少一个承担
  if (
    isTypeREIMBURSEMENT.value &&
    !liability.value.restrictOutInsure.liabilityFeeType?.some(
      (item) => item.open
    )
  ) {
    return {
      valid: false,
      message: "费用报销型责任，至少开启一个承担费用类型",
    };
  }

  if (
    isTypeREIMBURSEMENT.value &&
    liability.value.restrictOutInsure.type.includes("HOSPITAL") &&
    !liability.value.restrictOutInsure.visitType.length
  ) {
    return {
      valid: false,
      message: "费用报销型责任，请选择适用发票医疗类型",
    };
  }

  if (
    isTypeREIMBURSEMENT.value &&
    liability.value.restrictOutInsure.type.includes("HOSPITAL") &&
    !liability.value.restrictOutInsure.medicalInsurance
  ) {
    return {
      valid: false,
      message: "费用报销型责任，请选择医保使用情形",
    };
  }

  return { valid: true };
};

defineExpose({
  validate,
});
</script>

<template>
  <div>
    <el-form-item label="适用出险">
      <el-checkbox-group v-model="liability.restrictOutInsure.type">
        <el-checkbox value="HOSPITAL" :disabled="isTypeREIMBURSEMENT">
          医疗
        </el-checkbox>
        <el-checkbox value="ACCIDENT">意外</el-checkbox>
        <el-checkbox value="DISEASE">疾病</el-checkbox>
        <el-checkbox value="LIFE_INSURANCE">人寿</el-checkbox>
        <el-checkbox value="OTHER">其他</el-checkbox>
      </el-checkbox-group>
    </el-form-item>

    <!-- 医疗 -->
    <div
      v-if="
        liability.restrictOutInsure.type.includes('HOSPITAL') &&
        isTypeREIMBURSEMENT
      "
      class="ml-20"
    >
      <el-form-item label="适用发票医疗类型" label-width="130px">
        <el-checkbox-group v-model="liability.restrictOutInsure.visitType">
          <el-checkbox
            v-for="item in medicalTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-checkbox-group>
      </el-form-item>

      <el-form-item label="医保使用情形" label-width="100px" class="w-50%">
        <el-select
          v-model="liability.restrictOutInsure.medicalInsurance"
          clearable
        >
          <el-option label="医保后才能赔付" value="AFTER" />
          <el-option label="未使用医保赔付" value="BEFORE" />
          <el-option label="两种情形均赔付" value="BOTH" />
        </el-select>
      </el-form-item>

      <el-form-item label-width="0px">
        <div class="flex items-center justify-between w-100%">
          <div>
            发票费用类型
            <el-button type="primary" link size="small" class="ml-1">
              查看说明
            </el-button>
          </div>

          <el-button
            type="primary"
            link
            size="small"
            class="ml-1"
            @click="handleAddInvoiceFeeType"
            disabled
          >
            添加其他基金金额
          </el-button>
        </div>
        <el-table border :data="filteredInvoiceFeeTypes">
          <el-table-column label="发票金额信息" align="center" width="250px">
            <template #default="{ row }">
              <span
                v-if="row.isDefault"
                class="flex items-center justify-center"
              >
                <span
                  :class="{
                    'pl-15': row.parentFeeType,
                  }"
                >
                  {{ row.feeType }}
                </span>
                <el-button
                  v-if="hasChildInvoiceFeeTypes(row.feeType)"
                  link
                  round
                  :icon="!row.isExpanded ? 'ArrowDown' : 'ArrowUp'"
                  @click="row.isExpanded = !row.isExpanded"
                />
              </span>
              <span v-else class="flex items-center">
                <el-input v-model="row.feeType" />

                <el-button
                  type="danger"
                  class="ml-1"
                  link
                  @click="handleRemoveInvoiceFeeType(row)"
                >
                  <el-icon size="18px"><Remove /></el-icon>
                </el-button>
              </span>
            </template>
          </el-table-column>
          <el-table-column label="是否使用" align="center" width="150px">
            <template #default="{ row }">
              <el-switch v-model="row.open" />
            </template>
          </el-table-column>
          <el-table-column label="来源方式" align="center" width="150px">
            <template #default="{ row }">
              <el-select v-model="row.source" :disabled="row.isDefault">
                <el-option label="录入项" value="INPUT" />
                <el-option label="计算项" value="CALCULATE" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="计算项表达式" align="center">
            <template #default="{ row }">
              <div class="flex items-center" v-if="row.source === 'CALCULATE'">
                <span>
                  {{ row.calculateFormula }}
                </span>
                <el-button
                  class="ml-1"
                  v-if="!row.isDefault"
                  type="primary"
                  link
                  @click="setExpression(row)"
                >
                  设置表达式
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-form-item>

      <el-form-item label-width="0px">
        <div>
          承担费用类型
          <el-button type="primary" link size="small" class="ml-1">
            查看说明
          </el-button>
        </div>
        <el-table border :data="filteredLiabilityFeeTypes">
          <el-table-column label="承担费用类型" align="center" width="250px">
            <template #default="{ row }">
              <span
                v-if="row.isDefault"
                class="flex items-center justify-center"
              >
                <span
                  :class="{
                    'pl-15': row.parentFeeType,
                  }"
                >
                  {{ row.feeType }}
                </span>
                <el-button
                  v-if="hasChildLiabilityFeeTypes(row.feeType)"
                  link
                  round
                  :icon="!row.isExpanded ? 'ArrowDown' : 'ArrowUp'"
                  @click="handleLiabilityFeeTypeExpand(row)"
                />
              </span>
              <span v-else class="flex items-center">
                <el-input v-model="row.feeType" />

                <el-button
                  type="danger"
                  class="ml-1"
                  link
                  @click="handleRemoveLiabilityFeeType(row)"
                >
                  <el-icon size="18px"><Remove /></el-icon>
                </el-button>
              </span>
            </template>
          </el-table-column>
          <el-table-column label="是否承担" align="center" width="150px">
            <template #default="{ row }">
              <el-switch v-model="row.open" />
            </template>
          </el-table-column>
          <el-table-column label="来源方式" align="center" width="150px">
            <template #default="{ row }">
              <el-select v-model="row.source" :disabled="row.isDefault">
                <el-option label="录入项" value="INPUT" />
                <el-option label="计算项" value="CALCULATE" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="计算项表达式" align="center">
            <template #default="{ row }">
              <div class="flex items-center" v-if="row.source === 'CALCULATE'">
                <span>
                  {{ row.calculateFormula }}
                </span>
                <el-button
                  class="ml-1"
                  v-if="!row.isDefault"
                  type="primary"
                  link
                  @click="setExpression(row)"
                >
                  设置表达式
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
        <div class="flex items-center mt-2">
          <el-button type="primary" link @click="handleCreateLiabilityFeeType">
            新增费用类型
          </el-button>
          <span class="ml-2 text-[var(--el-color-warning)]">
            提醒：设置时请注意发票金额项是否重复承担，可能引发超赔情况。请根据责任条款正确配置
          </span>
        </div>
      </el-form-item>

      <SetExpressionDialog
        v-model="setExpressionDialog.visible"
        v-bind="setExpressionDialog.params"
        @close="setExpressionDialog.onClose"
        @confirm="setExpressionDialog.onConfirm"
      />
    </div>

    <div
      v-if="liability.restrictOutInsure.type.includes('ACCIDENT')"
      class="ml-20"
    >
      <el-form-item label="意外类型" class="w-50%">
        <el-select multiple v-model="liability.restrictOutInsure.accidentType">
          <el-option
            v-for="item in accidentTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        label="伤残等级"
        class="w-50%"
        v-if="
          liability.restrictOutInsure.accidentType.includes(
            'ACCIDENTAL_DISABILITY'
          )
        "
      >
        <el-select v-model="liability.restrictOutInsure.disabilityLevel">
          <el-option label="一级" value="1" />
          <el-option label="二级" value="2" />
          <el-option label="三级" value="3" />
          <el-option label="四级" value="4" />
          <el-option label="五级" value="5" />
          <el-option label="六级" value="6" />
        </el-select>
      </el-form-item>
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
