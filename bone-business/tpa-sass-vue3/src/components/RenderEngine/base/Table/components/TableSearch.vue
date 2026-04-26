<script setup>
import { BaseCompType } from "@/enums";
import { SearchModeEnum } from "@/enums/table/SearchModeEnum";
import SelectDropWithoutForm from "@/components/RenderEngine/base/SelectDropWithoutForm/index.vue";
import SelectCtrlWithoutForm from "@/components/RenderEngine/base/SelectCtrlWithoutForm/index.vue";
defineOptions({
  name: "PKTableSearch",
});

const emit = defineEmits(["search"]);
const props = defineProps({
  fieldList: {
    type: Array,
    required: true,
  },
});

// 查询方式，IN，LIKE，BETWEEN，EQUAL
const SEARCH_TYPE = {
  IN: "IN",
  LIKE: "LIKE",
  BETWEEN: "BETWEEN",
  EQUAL: "EQUAL",
};

const form = ref({});
const formRef = ref(null);

const rules = computed(() => {
  return props.fieldList
    .filter((field) => field.required === 1)
    .reduce(
      (acc, field) => ({
        ...acc,
        [field.bizCode]: [
          { required: true, message: `${field.title}不能为空` },
        ],
      }),
      {}
    );
});

const handleReset = () => {
  form.value = {};
  if (formRef.value) {
    formRef.value.resetFields();
  }
  emit("search", []);
};

const handleSearch = async () => {
  if (!formRef.value) return;

  const isValid = await formRef.value.validate();
  if (!isValid) return;

  const filteredForm = Object.fromEntries(
    Object.entries(form.value).filter(([key, value]) => value && value.trim())
  );

  const params = [];
  for (const key in filteredForm) {
    const field = props.fieldList.find((field) => field.bizCode === key);

    switch (field.componentType) {
      case BaseCompType.Input:
        params.push({
          field: key,
          value: form.value[key],
          type:
            field.searchMode === SearchModeEnum.PRECISE
              ? SEARCH_TYPE.EQUAL
              : SEARCH_TYPE.LIKE,
        });
        break;

      case BaseCompType.InputNum:
        params.push({
          field: key,
          value: form.value[key],
          type: SEARCH_TYPE.EQUAL,
        });
        break;

      case BaseCompType.SelectDrop:
        params.push({
          field: key,
          value: form.value[key],
          type: SEARCH_TYPE.IN,
        });
        break;

      case BaseCompType.SelectCtrl:
        params.push({
          field: key,
          value: form.value[key],
          type: SEARCH_TYPE.EQUAL,
        });
        break;

      case BaseCompType.DateTime:
      case BaseCompType.DateRange:
        params.push({
          field: key,
          value: form.value[key],
          type: SEARCH_TYPE.BETWEEN,
        });
        break;
      default:
        break;
    }
  }
  emit("search", params);
};

// 暴露方法给父组件
defineExpose({
  reset: handleReset,
});
</script>

<template>
  <div>
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="right"
      inline
    >
      <template v-for="item in props.fieldList" :key="item.id">
        <el-form-item
          :label="item.title"
          :prop="item.bizCode"
          :required="item.required === 1"
          style="width: 320px"
        >
          <!-- Input类型 -->
          <el-input
            v-if="item.componentType === BaseCompType.Input"
            v-model="form[item.bizCode]"
            style="width: 100%"
          />

          <!-- InputNumber类型 -->
          <el-input-number
            v-if="item.componentType === BaseCompType.InputNum"
            :controls="false"
            v-model="form[item.bizCode]"
            style="width: 100%"
          />

          <!-- SelectDrop类型 -->
          <SelectDropWithoutForm
            v-if="item.componentType === BaseCompType.SelectDrop"
            v-model="form[item.bizCode]"
            :field="item"
            style="width: 100%"
          />

          <!-- SelectCtrl类型 -->
          <SelectCtrlWithoutForm
            v-if="item.componentType === BaseCompType.SelectCtrl"
            v-model="form[item.bizCode]"
            :field="item"
            style="width: 100%"
          />

          <!-- Date类型 -->
          <el-date-picker
            v-if="
              item.componentType === BaseCompType.DateTime ||
              item.componentType === BaseCompType.DateRange
            "
            v-model="form[item.bizCode]"
            type="daterange"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            unlink-panels
            style="width: 100%"
          />
        </el-form-item>
      </template>

      <el-form-item>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
        <el-button type="warning" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style lang="scss" scoped>
:deep(.el-form--inline .el-form-item) {
  margin-right: 15px;
}

:deep(.el-input-number.is-without-controls .el-input__wrapper) {
  padding: 1px 11px !important;
}

:deep(.el-input-number .el-input__inner) {
  text-align: left;
}
</style>
