<script setup>
import { isUnaryOperator, OperatorEnum } from "@/enums/rule/OperatorEnum";
import { getOperatorOptionsByCompType } from "@/enums/rule/LinkageRuleMapping";
import { ValueTypeEnum, ValueTypeOptions } from "@/enums/rule/ValueTypeEnum";
import {
  TablePropertyTypeOptions,
  TablePropertyValueOptions,
} from "@/enums/rule/PropertyTypeEnum";
import FieldAPI from "@/api/field";
import TableAPI from "@/api/table";
import LinkageRuleAPI from "@/api/rule/linkageRule";
import { isEmpty } from "lodash-es";
import FixedValue from "../components/FixedValue.vue";
import ViewCard from "./View.vue";
import { BaseCompType } from "@/enums";
import { useDictStore } from "@/store";

const dictStore = useDictStore();

const emits = defineEmits(["add", "update", "edit"]);
const props = defineProps({
  fieldId: {
    type: String,
    default: "",
  },
  type: {
    type: String,
    default: "",
  },
  status: {
    type: String,
    default: "view",
  },
  rule: {
    type: Object,
    default: () => ({}),
  },
});

const isAdding = computed(() => props.status === "add");
const isUpdating = computed(() => props.status === "update");
const isViewing = computed(() => props.status === "view");

const sourceField = ref({});
const sourceOperatorOptions = ref([]);
const sourceSameTypeFieldOptions = ref([]);
const targetTableOptions = ref([]);

const currentRule = ref({});

const initCurrentRule = (value) => {
  currentRule.value = value;
};

// 获取当前字段的属性值（主要）
const initSourceFieldProps = async (id) => {
  try {
    sourceField.value = await FieldAPI.getFieldPropsById(id);
    console.log(sourceField.value);
  } catch (error) {
    console.log(error);
  }
};

//获取当前字段动态值选项（同页面相同组件类型的其他字段）
const getSameTypeFieldOptions = async (fieldId) => {
  return await FieldAPI.getSameTypeFieldList(fieldId);
};
const initSourceSameTypeFieldOptions = async () => {
  sourceSameTypeFieldOptions.value = await getSameTypeFieldOptions(
    props.fieldId
  );
};
//获取与当前字段匹配的操作符
const initSourceOperatorOptions = () => {
  sourceOperatorOptions.value = getOperatorOptionsByCompType(props.type);
};

//获取当前字段关联的表格
const initTargetTableOptions = async (fieldId) => {
  targetTableOptions.value = await TableAPI.getPageTableListByFieldId(fieldId);
};

const isStartWatch = ref(false);
const startWatchRule = () => {
  if (!isViewing.value && !isStartWatch.value) {
    isStartWatch.value = true;
    watch(
      () => props.fieldId,
      async (newVal) => {
        if (newVal) {
          initSourceOperatorOptions();
          initSourceSameTypeFieldOptions();
          initSourceFieldProps(newVal);
          initTargetTableOptions(newVal);
        }
      },
      { immediate: true }
    );
  }
};

watch(
  () => props.rule,
  (newVal) => {
    if (!isEmpty(newVal)) {
      initCurrentRule(newVal);
      startWatchRule();
    }
  },
  { immediate: true }
);

const showGridItem = (gridItem) => {
  switch (gridItem) {
    case "sourceValueType":
      return !isUnaryOperator(currentRule.value.sourceOperator);
    case "sourceValue":
      return !isUnaryOperator(currentRule.value.sourceOperator);
  }
};

const fieldChineseMap = {
  sourceOperator: "当前字段操作符",
  sourceValueType: "当前字段值类型",
  sourceValue: "当前字段比较值",
  tableId: "目标表格",
  attributeName: "目标表格属性名",
  attributeValue: "目标表格属性值",
};

const isAvailableRule = (rule) => {
  if (!rule) {
    ElMessage.error("规则不能为空");
    return false;
  }

  const requiredFields = ["sourceOperator", "tableId", "attributeName"];

  for (const field of requiredFields) {
    if (!rule[field]) {
      ElMessage.error(`请输入必要字段: ${fieldChineseMap[field]}`);
      return false;
    }
  }

  if (!isUnaryOperator(rule.sourceOperator)) {
    const sourceFields = ["sourceValueType", "sourceValue"];
    for (const field of sourceFields) {
      if (!rule[field]) {
        ElMessage.error(`请输入必要字段: ${fieldChineseMap[field]}`);
        return false;
      }
    }

    if (rule.sourceOperator === OperatorEnum.BETWEEN) {
      if (rule.sourceValueType !== ValueTypeEnum.fixed) {
        ElMessage.error("操作符为 介于 时，当前字段值类型必须为固定值");
        return false;
      }

      const sourceValue = rule.sourceValue;
      const regex = /^[\[\(].*?[,].*?[\]\)]$/;

      if (!regex.test(sourceValue)) {
        ElMessage.error("当前字段比较值格式错误，应为 [a,b] 或 (a,b)");
        return false;
      }
    }
  }

  const propertyFields = ["attributeValue"];

  for (const field of propertyFields) {
    if (
      rule[field] === undefined ||
      rule[field] === null ||
      rule[field] === ""
    ) {
      ElMessage.error(`请输入必要字段: ${fieldChineseMap[field]}`);
      return false;
    }
  }

  return true;
};

const getSelectDropValue = async (type, code, value) => {
  if (value === undefined || value === null) {
    return value;
  }
  const valueArray = typeof value === "string" && value.split(",");
  const dictLabels = await dictStore.getDictLabel(type, code, valueArray);
  // 如果value是数组，则返回数组中每个值对应的标签
  if (Array.isArray(valueArray)) {
    return valueArray.map((item) => dictLabels[item] || item).join(",");
  }
  return dictLabels[value] || value;
};

const getSelectCtrlValue = (value) => {
  try {
    if (!value) return value;
    const parsedValue = JSON.parse(value);
    if (!parsedValue?.desc?.length) return undefined;
    const validValues = parsedValue.desc.filter((item) => item && item.trim());
    return validValues.length ? validValues.join("/") : undefined;
  } catch (e) {
    return value;
  }
};

const handleSave = async (mode) => {
  if (!isAvailableRule(currentRule.value)) {
    return;
  }

  if (currentRule.value.sourceValueType === ValueTypeEnum.fixed) {
    switch (sourceField.value?.type) {
      case BaseCompType.SelectDrop:
        currentRule.value.sourceValueCn = await getSelectDropValue(
          sourceField.value.selectDatasource.type,
          sourceField.value.selectDatasource.code,
          currentRule.value.sourceValue
        );
        break;
      case BaseCompType.SelectCtrl:
        currentRule.value.sourceValueCn = getSelectCtrlValue(
          currentRule.value.sourceValue
        );
        break;
      default:
        currentRule.value.sourceValueCn = currentRule.value.sourceValue;
        break;
    }
  } else {
    currentRule.value.sourceValueCn = sourceSameTypeFieldOptions.value.find(
      (item) => item.id === currentRule.value.sourceValue
    )?.title;
  }

  try {
    if (mode === "add") {
      await LinkageRuleAPI.createFieldTableRule(currentRule.value);
      ElMessage.success("添加成功");
      emits("add");
    } else {
      await LinkageRuleAPI.updateFieldTableRule(currentRule.value);
      ElMessage.success("修改成功");
      emits("update");
    }
    isStartWatch.value = false;
  } catch (error) {
    console.log(error);
  }
};

const handleEdit = () => {
  emits("edit");
};

const handleSourceValueTypeChange = () => {
  currentRule.value.sourceValue = undefined;
};
</script>

<template>
  <el-card v-if="currentRule">
    <template #header>
      <div v-if="isAdding" class="flex justify-between items-center">
        <span>新增字段 - 表格规则</span>
        <div>
          <el-button type="primary" @click="handleSave('add')">确定</el-button>
        </div>
      </div>
      <div v-if="isUpdating" class="flex justify-between items-center">
        <span>修改字段 - 表格规则</span>
        <div>
          <el-button type="primary" @click="handleSave('update')">
            确定
          </el-button>
        </div>
      </div>
      <div v-if="isViewing" class="flex justify-between items-center">
        <span>字段 - 表格规则</span>
        <div>
          <el-button type="primary" @click="handleEdit">编辑</el-button>
        </div>
      </div>
    </template>

    <!-- 编辑态 -->
    <div class="grid-container" v-if="!isViewing">
      <div class="grid-item">
        <strong>当前字段</strong>
        的
        <strong>值</strong>
      </div>
      <div class="grid-item">
        <el-select v-model="currentRule.sourceOperator" placeholder="操作符">
          <el-option
            v-for="item in sourceOperatorOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
      <div class="grid-item">
        <template v-if="showGridItem('sourceValueType')">
          <el-select
            v-model="currentRule.sourceValueType"
            placeholder="值类型"
            @change="handleSourceValueTypeChange"
          >
            <el-option
              v-for="item in ValueTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </template>
      </div>
      <div class="grid-item col-span-2">
        <template v-if="showGridItem('sourceValue')">
          <fixed-value
            v-if="currentRule.sourceValueType === ValueTypeEnum.fixed"
            v-model="currentRule.sourceValue"
            :componentType="sourceField?.type"
            :selectDatasource="sourceField?.selectDatasource"
            :selectLevel="sourceField?.selectLevel"
          />
          <el-select
            v-else
            v-model="currentRule.sourceValue"
            placeholder="当前页面字段"
            filterable
          >
            <el-option
              v-for="item in sourceSameTypeFieldOptions"
              :key="item.id"
              :label="item.title"
              :value="item.id"
            />
          </el-select>
        </template>
      </div>
      <div class="grid-item">
        <strong>目标表格</strong>
      </div>
      <div class="grid-item col-span-4">
        <el-select
          placeholder="当前页面表格"
          v-model="currentRule.tableId"
          filterable
        >
          <el-option
            v-for="item in targetTableOptions"
            :key="item.id"
            :label="item.tableName"
            :value="item.id"
          />
        </el-select>
      </div>
      <div class="grid-item">
        <strong>属性</strong>
      </div>
      <div class="grid-item">
        <el-select placeholder="属性名" v-model="currentRule.attributeName">
          <el-option
            v-for="item in TablePropertyTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
      <div class="grid-item">
        <el-select placeholder="属性值" v-model="currentRule.attributeValue">
          <el-option
            v-for="item in TablePropertyValueOptions[currentRule.attributeName]"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </div>
    </div>

    <view-card v-if="isViewing" :rule="currentRule" />
  </el-card>
</template>

<style lang="scss" scoped>
.grid-container {
  display: grid;
  grid-template-rows: repeat(3, auto); /* 3行布局，行高根据内容自动调整 */
  grid-template-columns: repeat(5, 1fr); /* 5列布局 */
  gap: 10px; /* 设置列和行之间的间距 */
}

.grid-item {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  color: #606266;
}

:deep(.el-card__header) {
  padding: 8px var(--el-card-padding);
}
</style>
