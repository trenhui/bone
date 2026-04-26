<script setup lang="ts">
import ClaimAPI, { IHangupRecord } from "@/api/claim";
import { formatDate } from "@/utils/date";
defineOptions({
  name: "HangupRecordDialog",
});

const emit = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  claimNo: {
    type: String,
    default: "",
  },
});

const hangupRecordList = ref<IHangupRecord[]>([]);

const hangupTypeList = ref<{ label: string; value: string }[]>([
  { label: "其他", value: "1" },
  { label: "资料不齐（挂给客户）", value: "4" },
  { label: "资料不齐（暂存）", value: "6" },
  { label: "无承保信息-主被未承保", value: "7" },
  { label: "无承保信息-家属未承保", value: "8" },
  { label: "保单选择有误", value: "9" },
  { label: "公账额度不足", value: "10" },
  { label: "规则暂不明确", value: "11" },
]);

const getHangupRecordList = async () => {
  try {
    const res = await ClaimAPI.hangupRecord(props.claimNo);
    hangupRecordList.value = res;
  } catch (error) {
    console.error(error);
  }
};

const getHangupTypeCN = (reasonType: string) => {
  const type = hangupTypeList.value.find((item) => item.value === reasonType);
  return type?.label || "未知";
};

watch(
  () => dialogVisible.value,
  (val) => {
    if (val) {
      getHangupRecordList();
    }
  },
  { immediate: true }
);

const onClose = () => {
  dialogVisible.value = false;
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="onClose"
    title="挂起记录"
    width="50%"
    :close-on-click-modal="false"
  >
    <div class="p-2">
      <el-table
        border
        :data="hangupRecordList"
        style="width: 100%"
        max-height="400"
      >
        <el-table-column prop="hangUpTime" label="挂起时间" align="center">
          <template #default="scope">
            {{ formatDate(scope.row.hangUpTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="reasonType" label="挂起类型" align="center">
          <template #default="scope">
            {{ getHangupTypeCN(scope.row.reasonType) }}
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="挂起原因" align="center" />
        <el-table-column prop="explanation" label="客户反馈" align="center" />
      </el-table>
    </div>

    <template #footer>
      <div class="flex justify-center">
        <el-button @click="onClose">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped></style>
