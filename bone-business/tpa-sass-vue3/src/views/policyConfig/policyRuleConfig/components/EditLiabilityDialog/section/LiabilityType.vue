<script setup lang="ts">
import { LiabilityDTO } from "@/api/liability";
import { PropType } from "vue";
defineOptions({
  name: "LiabilityType",
});

const liability = defineModel("liability", {
  type: Object as PropType<LiabilityDTO>,
  required: true,
});
</script>

<template>
  <div>
    <el-form-item label="责任形式">
      <el-radio-group v-model="liability.liabilityType as any">
        <el-radio value="REIMBURSEMENT">费用报销型</el-radio>
        <el-radio value="FIXED_AMOUNT">定额给付型</el-radio>
        <el-radio value="ALLOWANCE">津贴给付型</el-radio>
      </el-radio-group>
    </el-form-item>

    <!-- 定额给付型 -->
    <div class="ml-20" v-if="liability.liabilityType === 'FIXED_AMOUNT'">
      <el-form-item label="给付依据">
        <el-radio-group v-model="liability.paymentBasis as any">
          <el-radio value="SEVERE">重疾标识</el-radio>
          <el-radio value="DISEASE" disabled>疾病种类</el-radio>
          <el-radio value="TOTAL_FEE" disabled>发票总费用</el-radio>
          <el-radio value="DISABILITY" disabled>失能标识</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-input type="textarea" disabled v-model="liability.paymentBasis" />
    </div>

    <!-- 津贴给付型 -->
    <div class="ml-20" v-if="liability.liabilityType === 'ALLOWANCE'">
      <el-form-item
        label="开始日期非保险期间"
        label-width="140px"
        class="w-60%"
      >
        <el-select v-model="liability.allowanceDetail.startOutPeriod">
          <el-option
            label="开始日期不在保险期间内或处于等待期内的，则拒赔"
            value="REFUSE"
          />
          <el-option
            label="开始日期不在保险期间内或处于等待期内的，则保险期间内可赔"
            value="PERIOD"
          />
        </el-select>
      </el-form-item>
      <el-table border :data="[1]">
        <el-table-column label="日津贴金额" align="center">
          <template #default>
            <el-input-number
              v-model="liability.allowanceDetail.allowancePerDay as any"
              :min="0"
              :precision="2"
              controls-position="right"
            />
          </template>
        </el-table-column>
        <el-table-column label="期间天数上限" align="center">
          <template #default>
            <el-input-number
              v-model="liability.allowanceDetail.inPeriodLimit as any"
              :min="0"
              :max="366"
              :step="1"
              :precision="0"
              controls-position="right"
            />
          </template>
        </el-table-column>
        <el-table-column label="期满天数上限" align="center">
          <template #default>
            <el-input-number
              v-model="liability.allowanceDetail.outPeriodLimit as any"
              :min="0"
              :max="366"
              :step="1"
              :precision="0"
              controls-position="right"
            />
          </template>
        </el-table-column>
        <el-table-column label="津贴天数计算方式" align="center">
          <template #default>
            <el-select v-model="liability.allowanceDetail.dayCountOption">
              <el-option
                label="自然日计算法（入出两天计两天）"
                value="NATURAL"
              />
              <el-option
                label="日历日计算法（入出两天计一天）"
                value="CALENDER"
              />
              <el-option
                label="24小时计算法（满24小时计一天）"
                value="TWENTY_FOUR_HOUR"
              />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
