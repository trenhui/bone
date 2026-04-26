<script setup>
import { InputStatusOptions } from "@/enums/baseComp/InputStatusEnum";
import { DisplayedOptions } from "@/enums/baseComp/DisplayedEnum";
import { RequiredOptions } from "@/enums/baseComp/RequiredEnum";

const emits = defineEmits(["editRule"]);
const config = defineModel("config", {
  type: Object,
  required: true,
});

const isTable = inject("isTable");
</script>

<template>
  <div class="mb-6">
    <div class="flex justify-between items-center">
      <p
        class="pl-2 mt-1 mb-2 border-l-3 border-l-solid border-l-[var(--el-color-primary)] color-[#606266] font-bold text-base"
      >
        通用属性
      </p>
      <el-button v-if="!isTable" type="primary" link @click="emits('editRule')">
        编辑动态规则
      </el-button>
    </div>
    <div class="px-2">
      <el-form-item label="输入状态">
        <el-select v-model="config.inputStatus" placeholder="请选择输入状态">
          <el-option
            v-for="item in InputStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="显示隐藏">
        <el-select v-model="config.displayed" placeholder="请选择显示隐藏">
          <el-option
            v-for="item in DisplayedOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="是否必填">
        <el-select v-model="config.required" placeholder="请选择是否必填">
          <el-option
            v-for="item in RequiredOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <slot></slot>
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
