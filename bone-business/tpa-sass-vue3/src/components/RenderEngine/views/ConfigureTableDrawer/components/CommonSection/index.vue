<script setup>
import { TableDisplayOptions } from "@/enums/table/TableDisplayEnum";
import { RequiredOptions } from "@/enums";
defineOptions({
  name: "CommonSection",
});

const tableConfig = defineModel("tableConfig", {
  type: Object,
  required: true,
});
</script>

<template>
  <div class="section">
    <p class="section-title">通用规则</p>
    <div class="section-content">
      <el-form-item label="显示隐藏">
        <el-select v-model="tableConfig.display" placeholder="请选择">
          <el-option
            v-for="item in TableDisplayOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="是否必填">
        <el-select v-model="tableConfig.requiredData" placeholder="请选择">
          <el-option
            v-for="item in RequiredOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
