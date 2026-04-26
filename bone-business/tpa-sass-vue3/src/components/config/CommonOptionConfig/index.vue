<script setup lang="ts">
import FieldAPI, { FieldDataSource } from "@/api/field";
import ConfigDataSourceDialog from "./ConfigDataSourceDialog.vue";
import { getDictSourceTypeName } from "@/enums/DictSourceTypeEnum";
defineOptions({
  name: "CommonOptionConfig",
});

const props = defineProps({
  bizIdentityCode: {
    type: String,
    default: "",
  },
});

const isBizIdengtity = computed(() => {
  return (
    props.bizIdentityCode !== "" &&
    props.bizIdentityCode !== null &&
    props.bizIdentityCode !== undefined
  );
});

const fieldOptionList = ref<FieldDataSource[]>([]);
const loading = ref(false);
const initFieldOptionList = async () => {
  try {
    loading.value = true;
    const res = await FieldAPI.getFieldDataSource(props.bizIdentityCode);
    fieldOptionList.value = res;
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  initFieldOptionList();
});

const configDataSourceDialog = ref({
  visible: false,
  params: {
    bizIdentityCode: "",
    fieldCode: "",
    sourceType: 0,
    sourceCode: "",
    fieldName: "",
    componentType: "",
    extraConfig: {
      optionSetOtherTag: false,
      optionSetOtherTitle: "",
      optionSetOtherNumberLimited: 0,
      optionSetOtherRequired: false,
      optionSetOtherMatchValue: "",
    },
  },
  onClose: (isRefresh: boolean = false) => {
    if (isRefresh) {
      initFieldOptionList();
    }
  },
});

const handleConfigDataSource = (row: FieldDataSource) => {
  const sourceType = isBizIdengtity.value
    ? row.exclusiveSourceType || 1
    : row.baseSourceType || 1;
  const sourceCode = isBizIdengtity.value
    ? row.exclusiveSourceCode || ""
    : row.baseSourceCode || "";

  configDataSourceDialog.value.params = {
    bizIdentityCode: props.bizIdentityCode,
    fieldCode: row.fieldCode,
    sourceType,
    sourceCode,
    fieldName: row.fieldName,
    componentType: row.componentType,
    extraConfig: row.extraConfig,
  };
  configDataSourceDialog.value.visible = true;
};
</script>

<template>
  <div>
    <div class="text-sm text-gray-500 m-2">
      <div>
        默认采用普康标准选项集，若配置主体专属选项集，则以主体专属为准。
      </div>
      <div class="mt-2">
        发布后请谨慎切换，切换后已处理赔案保留原选项值，适用于新签收赔案和作业中赔案。
      </div>
    </div>

    <el-table v-loading="loading" class="mt-4" :data="fieldOptionList" border>
      <el-table-column label="系统业务字段" prop="fieldName" align="center" />
      <el-table-column
        v-if="!isBizIdengtity"
        label="业务字段标识"
        prop="fieldCode"
        align="center"
      />
      <el-table-column v-if="!isBizIdengtity" label="数据源类型" align="center">
        <template #default="scope">
          {{ getDictSourceTypeName(scope.row.baseSourceType) }}
        </template>
      </el-table-column>
      <el-table-column
        label="普康标准选项集"
        prop="baseSourceName"
        align="center"
      />
      <el-table-column
        v-if="isBizIdengtity"
        label="主体专属选项集"
        prop="exclusiveSourceName"
        align="center"
      />
      <el-table-column label="操作" align="center">
        <template #default="scope">
          <el-button
            type="primary"
            link
            @click="handleConfigDataSource(scope.row)"
          >
            配置数据源
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <ConfigDataSourceDialog
      v-model="configDataSourceDialog.visible"
      v-bind="configDataSourceDialog.params"
      @close="configDataSourceDialog.onClose"
    />
  </div>
</template>

<style lang="scss" scoped></style>
