<script setup lang="ts">
import { useDictList, useDictSelect } from "@/hooks";
import FieldAPI, { ExtraConfig } from "@/api/field";
import { DictSourceTypeOptions } from "@/enums/DictSourceTypeEnum";
import { getBaseCompTypeName, BaseCompType } from "@/enums";
import { cloneDeep } from "lodash-es";
import { c } from "vite/dist/node/types.d-aGj9QkWt";
defineOptions({
  name: "ConfigDataSourceDialog",
});

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  bizIdentityCode: {
    type: String,
    default: "",
  },
  fieldCode: {
    type: String,
    default: "",
  },
  sourceType: {
    type: Number,
    default: 1,
  },
  sourceCode: {
    type: String,
    default: "",
  },
  fieldName: {
    type: String,
    default: "",
  },
  componentType: {
    type: String,
    default: "",
  },
  extraConfig: {
    type: Object as PropType<ExtraConfig>,
    default: () => ({}),
  },
});

const sourceType = ref(props.sourceType);
const sourceCode = ref(props.sourceCode);
const extraConfig = ref(props.extraConfig);
const isNeedRefresh = ref(false);

const { dictList, dictListMethods } = useDictList();
const { dictOptions, loadDictData, searchDictData } = useDictSelect(
  sourceType,
  sourceCode
);

// 处理数据源类型变化
async function handleSourceTypeChange(type: number) {
  if (!type) return;
  await dictListMethods.init(type);
  sourceCode.value = "";
  extraConfig.value.optionSetOtherMatchValue = "";
}

const handleSourceCodeChange = (code: string) => {
  extraConfig.value.optionSetOtherMatchValue = "";
};

watch(
  dialogVisible,
  async (newVal) => {
    if (!newVal) return;
    sourceType.value = props.sourceType;
    sourceCode.value = props.sourceCode;
    extraConfig.value = cloneDeep(props.extraConfig);

    if (sourceType.value) {
      await dictListMethods.init(sourceType.value);
      if (sourceCode.value) {
        dictListMethods.loadSelected(sourceType.value, sourceCode.value);
        loadDictData();
      }
    }
  },
  {
    immediate: true,
  }
);

const handleDictListSearch = (name: string) => {
  if (!sourceType.value) return;

  dictListMethods.search({ type: sourceType.value, name });
};

const handleClose = () => {
  dialogVisible.value = false;
  sourceType.value = 1;
  sourceCode.value = "";
  extraConfig.value = {
    optionSetOtherTag: false,
    optionSetOtherTitle: "",
    optionSetOtherNumberLimited: 0,
    optionSetOtherRequired: false,
    optionSetOtherMatchValue: "",
  };
  emits("close", isNeedRefresh.value);
  isNeedRefresh.value = false;
};

const handleConfirm = async () => {
  if (!sourceCode.value) {
    ElMessage.warning("请先选择数据源");
    return;
  }

  if (extraConfig.value.optionSetOtherTag) {
    if (!extraConfig.value.optionSetOtherMatchValue) {
      ElMessage.warning("开启其他选项时，请设置其他选项");
      return;
    }
  }

  await FieldAPI.setFieldDataSource({
    bizIdentityCode: props.bizIdentityCode,
    fieldCode: props.fieldCode,
    dataSourceType: sourceType.value,
    dataSourceCode: sourceCode.value,
    extraConfig: extraConfig.value,
  });
  ElMessage.success("设置数据源成功");
  isNeedRefresh.value = true;
  handleClose();
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    :close-on-click-modal="false"
    title="配置数据源"
    width="35%"
    top="20vh"
  >
    <div class="p-2">
      <el-form label-width="auto">
        <el-form-item label="业务字段">
          {{ props.fieldName }}
        </el-form-item>
        <el-form-item label="组件类型">
          {{ getBaseCompTypeName(props.componentType) }}
        </el-form-item>
        <el-form-item label="字段标识">
          {{ props.fieldCode }}
        </el-form-item>
        <el-form-item label="选项数据源">
          <el-row style="width: 100%">
            <el-col :span="6" class="pr-2">
              <el-select
                v-model="sourceType"
                placeholder="请选择类型"
                @change="handleSourceTypeChange"
                clearable
              >
                <el-option
                  v-for="item in DictSourceTypeOptions"
                  :key="item.type"
                  :label="item.name"
                  :value="item.type"
                />
              </el-select>
            </el-col>
            <el-col :span="18">
              <el-select
                v-model="sourceCode"
                placeholder="请选择选项数据源"
                filterable
                remote
                :remote-method="handleDictListSearch"
                remote-show-suffix
                v-loadMore="dictListMethods.loadMore"
                style="width: 100%"
                clearable
                @change="handleSourceCodeChange"
              >
                <el-option
                  v-for="item in dictList"
                  :key="item.code"
                  :label="item.name"
                  :value="item.code"
                />
              </el-select>
            </el-col>
          </el-row>
        </el-form-item>

        <!-- 支持其他配置 -->
        <template v-if="props.componentType === BaseCompType.SelectDrop">
          <el-form-item label="是否开启其他">
            <el-switch v-model="extraConfig.optionSetOtherTag" />
          </el-form-item>
          <el-form-item label="其他配置" v-if="extraConfig.optionSetOtherTag">
            <el-row :gutter="5">
              <el-col :span="12">
                <el-input
                  v-model="extraConfig.optionSetOtherTitle"
                  placeholder="占位文案"
                  clearable
                />
              </el-col>
              <el-col :span="6">
                <el-input
                  v-model.number="extraConfig.optionSetOtherNumberLimited"
                  placeholder="限制字数"
                  :min="1"
                  :max="100"
                  :formatter="(value: any) => value.replace(/\D/g, '')"
                  clearable
                />
              </el-col>
              <el-col :span="6">
                <el-select
                  v-model="extraConfig.optionSetOtherRequired"
                  placeholder="是否必填"
                  clearable
                >
                  <el-option label="是" :value="true" />
                  <el-option label="否" :value="false" />
                </el-select>
              </el-col>
            </el-row>
          </el-form-item>
          <el-form-item
            label="设置其他选项"
            v-if="extraConfig.optionSetOtherTag"
          >
            <el-select
              v-model="extraConfig.optionSetOtherMatchValue"
              placeholder="请选择其他选项"
              filterable
              remote
              :remote-method="searchDictData"
              remote-show-suffix
              style="width: 100%"
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
        </template>
      </el-form>
    </div>

    <template #footer>
      <div class="flex justify-center">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确定</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
:deep(.el-form-item) {
  margin-bottom: 4px;
}
</style>
