<script setup lang="ts">
import ClaimAPI, { IHistoryCase } from "@/api/claim";
import { h } from "vue";
import { ElButton, TableV2FixedDir } from "element-plus";
import type { Column } from "element-plus";

defineOptions({
  name: "HistoryCaseDialog",
});

const emit = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  claimNo: {
    type: String,
    default: "",
  },
  outIdentityNo: {
    type: String,
    default: "",
  },
  outUserName: {
    type: String,
    default: "",
  },
});

const historyCaseList = ref<IHistoryCase[]>([]);

const getHistoryCaseList = async () => {
  try {
    const res = await ClaimAPI.queryHistoryClaims({
      claimNo: props.claimNo,
      outIdentityNo: props.outIdentityNo,
      outUserName: props.outUserName,
    });
    historyCaseList.value = res;
  } catch (error) {
    console.error(error);
  }
};

watch(
  () => dialogVisible.value,
  (val) => {
    if (val) {
      getHistoryCaseList();
    }
  },
  { immediate: true }
);

const onClose = () => {
  dialogVisible.value = false;
};

const getRowClassName = ({ rowIndex }: { rowIndex: number }) => {
  const row = historyCaseList.value[rowIndex];
  if (!row) return "";
  switch (row.colorMark) {
    case 1:
      return "warning-row";
    case 2:
      return "review-row";
    case 3:
      return "copy-row";
    default:
      return "";
  }
};

const toHistoryDetail = (row: IHistoryCase) => {
  console.log(row);
};

// 定义表格列配置
const columns: Column<IHistoryCase>[] = [
  {
    key: "index",
    title: "序号",
    dataKey: "index",
    width: 70,
    align: "center",
    cellRenderer: ({ rowIndex }: any) => rowIndex + 1,
  },
  {
    key: "claimNo",
    title: "赔案号",
    dataKey: "claimNo",
    width: 160,
    align: "center",
    fixed: true,
  },
  {
    key: "taskNo",
    title: "保司任务号",
    dataKey: "taskNo",
    width: 180,
    align: "center",
  },
  {
    key: "claimStatus",
    title: "当前环节",
    dataKey: "claimStatus",
    width: 120,
    align: "center",
  },
  {
    key: "policyNo",
    title: "保单号",
    dataKey: "policyNo",
    width: 180,
    align: "center",
  },
  {
    key: "isReject",
    title: "赔付结论",
    dataKey: "isReject",
    width: 120,
    align: "center",
  },
  {
    key: "compensationAmount",
    title: "赔付金额",
    dataKey: "compensationAmount",
    width: 120,
    align: "center",
  },
  {
    key: "publicAmount",
    title: "公账赔付金额",
    dataKey: "publicAmount",
    width: 150,
    align: "center",
  },
  {
    key: "auditUser",
    title: "审核人",
    dataKey: "auditUser",
    width: 120,
    align: "center",
  },
  {
    key: "auditDate",
    title: "审核时间",
    dataKey: "auditDate",
    width: 180,
    align: "center",
  },
  {
    key: "personName",
    title: "被保险人姓名",
    dataKey: "personName",
    width: 140,
    align: "center",
  },
  {
    key: "personCertId",
    title: "被保险人证件号",
    dataKey: "personCertId",
    width: 180,
    align: "center",
  },
  {
    key: "insuranceName",
    title: "保险公司",
    dataKey: "insuranceName",
    width: 150,
    align: "center",
  },
  {
    key: "insureName",
    title: "投保公司",
    dataKey: "insureName",
    width: 150,
    align: "center",
  },
  {
    key: "actions",
    title: "操作",
    dataKey: "actions",
    width: 120,
    align: "center",
    fixed: TableV2FixedDir.RIGHT,
    cellRenderer: ({ rowData }: any) => {
      return h(
        ElButton,
        {
          type: "primary",
          size: "small",
          disabled:
            rowData.claimStatus == "录入" ||
            rowData.claimStatus == "签收" ||
            rowData.claimStatus == "初审",
          onClick: () => toHistoryDetail(rowData),
        },
        () => "查看"
      );
    },
  },
];
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="onClose"
    title="历史记录"
    width="90vw"
    top="5vh"
    :close-on-click-modal="false"
  >
    <div class="p-2 h-[75vh]">
      <el-auto-resizer>
        <template #default="{ height, width }">
          <el-table-v2
            :columns="columns"
            :data="historyCaseList"
            :width="width"
            :height="height"
            :row-class="getRowClassName"
            fixed
          />
        </template>
      </el-auto-resizer>
    </div>

    <template #footer>
      <div class="flex justify-center">
        <el-button @click="onClose">关闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
:deep(.warning-row) {
  background-color: #e6a23c;
}

:deep(.review-row) {
  background-color: #c280ff;
}

:deep(.copy-row) {
  background-color: #555;

  .el-table-v2__row-cell.is-align-center {
    color: #fff !important;
  }
}
</style>
