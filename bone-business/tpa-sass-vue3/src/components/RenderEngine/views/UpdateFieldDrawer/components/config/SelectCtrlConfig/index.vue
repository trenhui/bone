<script setup>
import FieldInfoSection from "../../sections/FieldInfoSection/index.vue";
import BasePropSection from "../../sections/BasePropSection/index.vue";
import PromptSection from "../../sections/PromptSection/index.vue";
import CompPropSection from "../../sections/CompPropSection/index.vue";
import CommonPropSection from "../../sections/CommonPropSection/index.vue";
import { useDictList } from "@/hooks";
import { useDictSelect } from "@/hooks";
import { DictSourceTypeOptions } from "@/enums/DictSourceTypeEnum";
import {
  SelectLevelEnum,
  SelectLevelOptions,
} from "@/enums/baseComp/SelectLevelEnum";

const emits = defineEmits(["editRule"]);
const config = defineModel("config", {
  type: Object,
  required: true,
});

const { dictList, dictListMethods } = useDictList();

const selectState = reactive({
  sourceType: config.value.selectDatasource?.type,
  sourceCode: config.value.selectDatasource?.code,
});

const maxLevel = computed(() => {
  return config.value.selectLevel === SelectLevelEnum.THREE ? 2 : 1;
});

const { dictOptions, loadCascaderData } = useDictSelect(
  selectState.sourceType,
  selectState.sourceCode,
  {
    maxLevel: maxLevel.value,
    isCascader: true,
  }
);

// 初始化
onMounted(async () => {
  if (selectState.sourceType) {
    await dictListMethods.init(selectState.sourceType);
    if (selectState.sourceCode) {
      dictListMethods.loadSelected(
        selectState.sourceType,
        selectState.sourceCode
      );
    }
  }
});

// 级联选择器配置
const cascaderProps = {
  value: "code",
  label: "name",
  children: "children",
  checkStrictly: true,
  lazy: true,
  lazyLoad: loadCascaderData,
};

const cascaderKey = ref(0);
const cascaderRef = ref(null);

const displayDefaultValue = computed({
  get: () => {
    if (!config.value.defaultValue) return [];
    try {
      const parsed = JSON.parse(config.value.defaultValue);
      return parsed.code || [];
    } catch {
      return [];
    }
  },
  set: (val) => {
    if (!val || val.length === 0) {
      config.value.defaultValue = "";
      return;
    }

    // 获取选中节点的完整数据
    const checkedNodes = cascaderRef.value.getCheckedNodes()[0];
    if (!checkedNodes) return;

    config.value.defaultValue = JSON.stringify({
      code: checkedNodes.pathValues,
      desc: checkedNodes.pathLabels,
    });
  },
});

// 重置级联选择器
watch([() => selectState.sourceCode, () => maxLevel.value], () => {
  displayDefaultValue.value = null;
  config.value.defaultValue = null;
  cascaderKey.value += 1;
});
</script>

<template>
  <div class="select-ctrl-config">
    <el-form :model="config" label-width="auto" label-position="right">
      <!-- 字段信息 -->
      <field-info-section v-model:config="config" />

      <!-- 基础属性 -->
      <base-prop-section v-model:config="config" />

      <!-- 组件提示 -->
      <prompt-section v-model:config="config" />

      <!-- 组件专属属性 -->
      <comp-prop-section v-model:config="config">
        <el-form-item label="选项层级">
          <el-select
            v-model="config.selectLevel"
            placeholder="请选择选项选项层级"
          >
            <el-option
              v-for="item in SelectLevelOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="选项数据源">
          <el-row style="width: 100%">
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
            <el-col :span="18">
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
          </el-row>
        </el-form-item>
      </comp-prop-section>

      <!-- 组件通用属性 -->
      <common-prop-section
        v-model:config="config"
        @edit-rule="emits('editRule')"
      >
        <el-form-item label="默认值">
          <el-cascader
            ref="cascaderRef"
            v-model="displayDefaultValue"
            placeholder="请输入默认值"
            style="width: 100%"
            :props="cascaderProps"
            :key="cascaderKey"
            clearable
          />
        </el-form-item>
      </common-prop-section>
    </el-form>
  </div>
</template>

<style lang="scss" scoped></style>
