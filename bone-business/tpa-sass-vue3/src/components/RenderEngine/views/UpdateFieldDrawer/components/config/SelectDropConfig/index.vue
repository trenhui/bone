<script setup>
import ConfigLinkageOptionFieldDialog from "@/components/RenderEngine/views/ConfigLinkageOptionFieldDialog/index.vue";
import FieldInfoSection from "../../sections/FieldInfoSection/index.vue";
import BasePropSection from "../../sections/BasePropSection/index.vue";
import PromptSection from "../../sections/PromptSection/index.vue";
import CompPropSection from "../../sections/CompPropSection/index.vue";
import CommonPropSection from "../../sections/CommonPropSection/index.vue";
import {
  SelectTypeOptions,
  SelectTypeEnum,
} from "@/enums/baseComp/SelectTypeEnum";
import {
  FilterTypeOptions,
  FilterTypeEnum,
} from "@/enums/baseComp/FilterTypeEnum";
import { useDictList } from "@/hooks";
import { useDictSelect } from "@/hooks";
import { DictSourceTypeOptions } from "@/enums/DictSourceTypeEnum";

const emits = defineEmits(["editRule"]);
const config = defineModel("config", {
  type: Object,
  required: true,
});

const props = defineProps({
  isTable: {
    type: Boolean,
    default: false,
  },
});

const selectState = reactive({
  sourceType: config.value.selectDatasource?.type,
  sourceCode: config.value.selectDatasource?.code,
  displayDefaultValue: initDisplayDefaultValue(),
});

function initDisplayDefaultValue() {
  return config.value.selectType === SelectTypeEnum.multiple &&
    config.value.defaultValue
    ? config.value.defaultValue.split(",")
    : config.value.defaultValue;
}

const { dictList, dictListMethods } = useDictList();
const {
  dictOptions,
  searchDictData,
  loadMoreDictData,
  loadSelectedDictData,
  loadDictData,
} = useDictSelect(selectState.sourceType, selectState.sourceCode);

// 初始化
onMounted(async () => {
  if (selectState.sourceType) {
    if (selectState.sourceCode) {
      dictListMethods.loadSelected(
        selectState.sourceType,
        selectState.sourceCode
      );
      // 初始化加载数据
      await loadDictData(selectState.sourceCode);
      if (config.value.defaultValue) {
        await loadSelectedDictData(config.value.defaultValue);
      }
    }
  }
});

// 处理 selectType 变化
watch(
  () => config.value.selectType,
  (newVal) => {
    if (newVal === SelectTypeEnum.multiple) {
      selectState.displayDefaultValue = selectState.displayDefaultValue
        ? selectState.displayDefaultValue.split(",")
        : [];
    } else {
      selectState.displayDefaultValue =
        Array.isArray(selectState.displayDefaultValue) &&
        selectState.displayDefaultValue.length > 0
          ? selectState.displayDefaultValue[0]
          : undefined;
    }
  }
);

// 处理 displayDefaultValue 变化
watch(
  () => selectState.displayDefaultValue,
  (newVal) => {
    config.value.defaultValue = Array.isArray(newVal)
      ? newVal.join(",")
      : newVal;
  }
);

const configDialog = ref({
  visible: false,
  params: {
    fieldId: "",
    fieldName: "",
    componentType: "",
    sourceType: 0,
    sourceCode: "",
    sourceName: "",
    isTable: props.isTable,
  },
});
const handleConfigLinkageOptionField = () => {
  if (!selectState.sourceCode) {
    ElMessage.warning("请先选择选项数据源");
    return;
  }

  configDialog.value.params = {
    fieldId: config.value.id,
    fieldName: config.value.showName,
    componentType: config.value.type,
    sourceType: selectState.sourceType,
    sourceCode: selectState.sourceCode,
    sourceName: dictList.value.find(
      (item) => item.code === selectState.sourceCode
    )?.name,
    isTable: props.isTable,
  };

  configDialog.value.visible = true;
};
</script>

<template>
  <div class="select-drop-config">
    <el-form :model="config" label-width="auto" label-position="right">
      <!-- 字段信息 -->
      <field-info-section v-model:config="config" />

      <!-- 基础属性 -->
      <base-prop-section v-model:config="config" />

      <!-- 组件提示 -->
      <prompt-section v-model:config="config" />

      <!-- 组件专属属性 -->
      <comp-prop-section v-model:config="config">
        <el-form-item label="选项方式">
          <el-select v-model="config.selectType" placeholder="请选择选项方式">
            <el-option
              v-for="item in SelectTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="选项筛选">
          <el-select
            v-model="config.filterType"
            placeholder="请选择是否支持筛选"
          >
            <el-option
              v-for="item in FilterTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="选项数据源">
          <el-row style="width: 100%" justify="space-evenly">
            <el-col :span="6" class="pr-2">
              <el-select
                v-model="selectState.sourceType"
                placeholder="请选择类型"
                disabled
              >
                <el-option
                  v-for="item in DictSourceTypeOptions"
                  :key="item.type"
                  :label="item.name"
                  :value="item.type"
                />
              </el-select>
            </el-col>
            <el-col :span="14">
              <el-select
                v-model="selectState.sourceCode"
                placeholder="请选择选项数据源"
                style="width: 100%"
                disabled
              >
                <el-option
                  v-for="item in dictList"
                  :key="item.code"
                  :label="item.name"
                  :value="item.code"
                />
              </el-select>
            </el-col>
            <el-col :span="4">
              <el-button
                type="primary"
                link
                @click="handleConfigLinkageOptionField"
              >
                联动展示字段
              </el-button>
            </el-col>
          </el-row>
        </el-form-item>
      </comp-prop-section>

      <!-- 组件通用属性 -->
      <common-prop-section
        v-model:config="config"
        @edit-rule="emits('editRule')"
      >
        <el-form-item label="默认值">
          <el-select
            v-model="selectState.displayDefaultValue"
            placeholder="请输入默认值"
            style="width: 100%"
            remote
            :remote-method="searchDictData"
            remote-show-suffix
            v-loadMore="loadMoreDictData"
            :multiple="config.selectType === SelectTypeEnum.multiple"
            :filterable="config.filterType === FilterTypeEnum.Supported"
            clearable
          >
            <el-option
              v-for="item in dictOptions"
              :key="item.code"
              :label="item.name"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
      </common-prop-section>
    </el-form>
  </div>

  <config-linkage-option-field-dialog
    v-model="configDialog.visible"
    v-bind="configDialog.params"
  />
</template>

<style lang="scss" scoped></style>
