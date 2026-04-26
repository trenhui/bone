<script setup lang="ts">
import ClaimAPI, { IOutEntryRecord } from "@/api/claim";
defineOptions({
  name: "OutEntryRecordDialog",
});

const emit = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  claimNo: {
    type: String,
    default: "",
  },
});

const recordList = ref<IOutEntryRecord[]>([]);

const getOutWriterRecords = async () => {
  try {
    const res = await ClaimAPI.getOutWriterRecords(props.claimNo);
    recordList.value = res;
  } catch (error) {
    console.error(error);
  }
};

watch(
  () => dialogVisible.value,
  (val) => {
    if (val) {
      getOutWriterRecords();
    }
  },
  { immediate: true }
);

const onClose = () => {
  dialogVisible.value = false;
};

const viewAllMessage = (row: IOutEntryRecord, type: string) => {
  if (type === "original") {
    const writeJson = JSON.stringify(JSON.parse(row.writeJson), null, 2);
    const newWindow = window.open("", "_blank");
    newWindow?.document.write(`
          <html>
            <head>
              <style>
                body { font-family: 'Courier New', Courier, monospace; margin: 20px; }
                pre { background-color: #f4f4f4; padding: 15px; border-radius: 5px; }
              </style>
            </head>
            <body>
              <pre>${writeJson}</pre>
            </body>
          </html>
        `);
  } else if (type === "translation") {
    const writeChinese = JSON.stringify(JSON.parse(row.writeChinese), null, 2);
    const newWindow = window.open("", "_blank");
    newWindow?.document.write(`
          <html>
            <head>
              <style>
                body { font-family: 'Courier New', Courier, monospace; margin: 20px; }
                pre { background-color: #f4f4f4; padding: 15px; border-radius: 5px; }
              </style>
            </head>
            <body>
              <pre>${writeChinese}</pre>
            </body>
          </html>
        `);
  }
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="onClose"
    title="外包录入记录"
    width="55%"
    :close-on-click-modal="false"
  >
    <div class="p-2">
      <el-table border :data="recordList" style="width: 100%" height="300">
        <el-table-column type="index" label="序号" align="center" width="60">
          <template #default="scope">
            {{ scope.$index + 1 }}
          </template>
        </el-table-column>
        <el-table-column prop="claimNumber" label="赔案号" align="center" />
        <el-table-column prop="writeDate" label="外包回传时间" align="center" />
        <el-table-column prop="writeUser" label="录入人" align="center" />
        <el-table-column label="操作" align="center" width="250">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              @click="viewAllMessage(row, 'original')"
            >
              查看原始报文
            </el-button>
            <el-button
              size="small"
              type="primary"
              @click="viewAllMessage(row, 'translation')"
            >
              查看转译报文
            </el-button>
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
