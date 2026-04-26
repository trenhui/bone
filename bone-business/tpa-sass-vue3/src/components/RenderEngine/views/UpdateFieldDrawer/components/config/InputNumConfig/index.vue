<script setup>
import FieldInfoSection from "../../sections/FieldInfoSection/index.vue";
import BasePropSection from "../../sections/BasePropSection/index.vue";
import PromptSection from "../../sections/PromptSection/index.vue";
import CompPropSection from "../../sections/CompPropSection/index.vue";
import CommonPropSection from "../../sections/CommonPropSection/index.vue";
import { DataFormatOptions } from "@/enums/baseComp/DataFormatEnum";

const emits = defineEmits(["editRule"]);
const config = defineModel("config", {
  type: Object,
  required: true,
});
</script>

<template>
  <div class="input-num-config">
    <el-form :model="config" label-width="auto" label-position="right">
      <!-- 字段信息 -->
      <field-info-section v-model:config="config" />

      <!-- 基础属性 -->
      <base-prop-section v-model:config="config" />

      <!-- 组件提示 -->
      <prompt-section v-model:config="config" />

      <!-- 组件专属属性 -->
      <comp-prop-section v-model:config="config">
        <el-form-item label="数据格式">
          <el-select v-model="config.dataFormat" placeholder="请选择数据格式">
            <el-option
              v-for="item in DataFormatOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="最小值">
          <el-input-number
            controls-position="right"
            style="width: 100%"
            v-model="config.min"
            :max="999999999"
            clearable
          />
        </el-form-item>
        <el-form-item label="最大值">
          <el-input-number
            controls-position="right"
            style="width: 100%"
            v-model="config.max"
            :max="999999999"
            clearable
          />
        </el-form-item>
        <el-form-item label="小数位数">
          <el-input-number
            controls-position="right"
            style="width: 100%"
            v-model="config.decimalDigit"
            :min="0"
            :max="9"
            :step="1"
            step-strictly
            clearable
          />
        </el-form-item>
        <el-form-item label="单位倍数">
          <el-input-number
            controls-position="right"
            style="width: 100%"
            v-model="config.multiples"
            :min="0"
            :max="999999999"
            clearable
          />
        </el-form-item>
      </comp-prop-section>

      <!-- 组件通用属性 -->
      <common-prop-section
        v-model:config="config"
        @edit-rule="emits('editRule')"
      >
        <el-form-item label="默认值">
          <el-input-number
            v-model="config.defaultValue"
            placeholder="请输入默认值"
            style="width: 100%"
            :controls="false"
            :min="config.min || undefined"
            :max="config.max || undefined"
            :step="config.multiples || undefined"
            :step-strictly="config.multiples ? true : false"
            :precision="config.decimalDigit || undefined"
            clearable
          />
        </el-form-item>
      </common-prop-section>
    </el-form>
  </div>
</template>

<style lang="scss" scoped></style>
