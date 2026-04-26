<script setup lang="ts">
import ClaimAPI, { IClaimRecord } from "@/api/claim";
import { formatDate } from "@/utils/date";
defineOptions({
  name: "OperateRecordDialog",
});

const emit = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  claimNo: {
    type: String,
    default: "",
  },
});

const operateRecordList = ref<IClaimRecord[]>([]);
const getOperateRecordList = async () => {
  try {
    const res = await ClaimAPI.record(props.claimNo);
    operateRecordList.value = res;
  } catch (error) {
    console.error(error);
  }
};

watch(
  () => dialogVisible.value,
  (val) => {
    if (val) {
      getOperateRecordList();
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
    title="赔案操作记录"
    width="50%"
    :close-on-click-modal="false"
  >
    <div class="p-2">
      <el-form-item label="赔案号">
        {{ claimNo }}
      </el-form-item>
      <el-table
        border
        :data="operateRecordList"
        style="width: 100%"
        max-height="400"
      >
        <el-table-column prop="operation" label="操作内容" align="center" />
        <el-table-column prop="createBy" label="操作人" align="center" />
        <el-table-column prop="createTime" label="操作时间" align="center">
          <template #default="scope">
            {{ formatDate(scope.row.createTime) }}
          </template>
        </el-table-column>
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
