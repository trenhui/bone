<script setup>
import FieldInfoSection from "../../sections/FieldInfoSection/index.vue";
import BasePropSection from "../../sections/BasePropSection/index.vue";
import PromptSection from "../../sections/PromptSection/index.vue";
import CompPropSection from "../../sections/CompPropSection/index.vue";
import CommonPropSection from "../../sections/CommonPropSection/index.vue";
import { InputValueTypeOptions } from "@/enums/baseComp/InputValueTypeEnum";

const emits = defineEmits(["editRule"]);
const config = defineModel("config", {
  type: Object,
  required: true,
});
</script>

<template>
  <div class="input-config">
    <el-form :model="config" label-width="auto" label-position="right">
      <!-- 字段信息 -->
      <field-info-section v-model:config="config" />

      <!-- 基础属性 -->
      <base-prop-section v-model:config="config" />

      <!-- 组件提示 -->
      <prompt-section v-model:config="config" />

      <!-- 组件专属属性 -->
      <comp-prop-section v-model:config="config">
        <el-form-item label="限制字数">
          <el-input-number
            controls-position="right"
            style="width: 100%"
            v-model="config.limitedLength"
            :min="0"
            :max="9999"
            :step="1"
            step-strictly
          />
        </el-form-item>
      </comp-prop-section>

      <!-- 组件通用属性 -->
      <common-prop-section
        v-model:config="config"
        @edit-rule="emits('editRule')"
      >
        <el-form-item label="默认值">
          <el-input
            v-model="config.defaultValue"
            placeholder="请输入默认值"
            style="width: 100%"
            :maxlength="config.limitedLength"
            clearable
          />
        </el-form-item>
        <el-form-item label="值类型">
          <el-select
            v-model="config.valueType"
            placeholder="请选择值类型"
            style="width: 100%"
          >
            <el-option
              v-for="item in InputValueTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </common-prop-section>
    </el-form>
  </div>
</template>

<style lang="scss" scoped></style>
