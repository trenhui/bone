<script setup lang="ts">
import FileAPI from "@/api/pk-file";
import { formatDate } from "@/utils/date";
defineOptions({
  name: "UploadRecordDialog",
});

const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  id: string;
  tenantId: string;
}>();

const uploadRecords = ref([]);
const loading = ref(false);
const init = async () => {
  loading.value = true;
  try {
    const res = await FileAPI.getRecord(props.id, "IMAGE", props.tenantId);
    uploadRecords.value = res;
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  init();
});

const innerVisible = ref(false);
const innerData = ref();
const handleToResult = (row: any) => {
  innerVisible.value = true;
  innerData.value = row;
};

const onClose = () => {
  dialogVisible.value = false;
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="onClose"
    :close-on-click-modal="false"
    title="上传影像历史"
    width="70%"
  >
    <div class="dialog_context h-[400px]">
      <el-table
        v-loading="loading"
        :data="uploadRecords"
        border
        show-overflow-tooltip
      >
        <el-table-column prop="fileName" label="影像件包名称" align="center" />
        <el-table-column prop="importType" label="导入方式" align="center" />
        <el-table-column
          prop="sameFileRule"
          label="同名文件处理"
          align="center"
        />
        <el-table-column prop="createTime" label="开始时间" align="center">
          <template #default="{ row }">
            {{ formatDate(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="完成时间" align="center">
          <template #default="{ row }">
            {{ formatDate(row.updateTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="name" label="操作结果" align="center">
          <template #default="{ row }">
            <el-button
              type="primary"
              size="small"
              link
              @click="handleToResult(row)"
            >
              查看
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="createBy" label="操作人员" align="center" />
      </el-table>
    </div>

    <el-dialog
      v-model="innerVisible"
      width="40%"
      title="操作结果"
      append-to-body
    >
      <div class="dialog_context">
        <span class="whitespace-pre-wrap">{{ innerData.extraInfo }}</span>
      </div>
    </el-dialog>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog_context {
  max-height: 500px;
  padding: 20px;
  overflow-y: auto;
}
</style>
