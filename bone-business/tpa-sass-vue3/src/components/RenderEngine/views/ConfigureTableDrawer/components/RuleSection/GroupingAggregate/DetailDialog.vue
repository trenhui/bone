<script setup>
import { ref } from "vue";
import TableAPI from "@/api/table";
import AggregateColumns from "./AggregateColumns.vue";
import UpdateDialog from "./UpdateDialog.vue";
defineOptions({
  name: "SummaryRowDialog",
});

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps({
  tableName: {
    type: String,
    default: "",
  },
  tableId: {
    type: String,
    default: "",
  },
});

const aggregateRules = ref([]);
const tableLoading = ref(false);
const confirmLoading = ref(false);

const initAggregateRules = async () => {
  if (!props.tableId) return;
  try {
    aggregateRules.value = await TableAPI.getAggregateRule(props.tableId);
  } catch (error) {
    console.error(error);
  }
};

watch(
  () => props.tableId,
  (newVal) => {
    if (newVal) {
      initAggregateRules();
    }
  },
  { immediate: true }
);

const handleClose = () => {
  dialogVisible.value = false;
  aggregateRules.value = [];
  tableLoading.value = false;
  confirmLoading.value = false;
  emits("close");
};
const handleConfirm = () => {
  handleClose();
};

const updateDialog = ref({
  visible: false,
  params: { tableId: "", tableName: "", originalRule: {}, isCreate: true },
  onClose: (isChange = false) => {
    updateDialog.value.visible = false;
    updateDialog.value.params = {
      tableId: "",
      tableName: "",
      originalRule: {},
      isCreate: true,
    };
    if (isChange) {
      initAggregateRules();
    }
  },
});

const handleAdd = () => {
  updateDialog.value.visible = true;
  updateDialog.value.params = {
    tableId: props.tableId,
    tableName: props.tableName,
    originalRule: {},
    isCreate: true,
  };
};

const handleEdit = (row) => {
  updateDialog.value.visible = true;
  updateDialog.value.params = {
    tableId: props.tableId,
    tableName: props.tableName,
    originalRule: row,
    isCreate: false,
  };
};

const handleDelete = (row) => {
  ElMessageBox.confirm("确定删除该分组聚合规则？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
  })
    .then(async () => {
      await TableAPI.deleteAggregateRule(row.id);
      ElMessage.success("删除成功");
      initAggregateRules();
    })
    .catch(() => {});
};
</script>

<template>
  <div>
    <el-dialog
      v-model="dialogVisible"
      :before-close="handleClose"
      title="查看分组聚合"
      width="45%"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
    >
      <div class="dialog-content">
        <div class="flex items-center mb-3 ml-1">
          <span class="">当前表格</span>
          <span class="ml-2 font-bold table-title">{{ `${tableName}` }}</span>
        </div>

        <el-button type="primary" link icon="Plus" @click="handleAdd">
          添加聚合规则
        </el-button>

        <div class="mt-3">
          <p>已有聚合规则</p>
          <template v-if="aggregateRules.length > 0">
            <div
              v-for="(rule, index) in aggregateRules"
              :key="index"
              class="mb-4"
            >
              <div class="flex items-center mb-2">
                <span class="mr-3">{{ rule.name }}</span>
                <el-button
                  type="warning"
                  link
                  icon="Edit"
                  @click="handleEdit(rule)"
                >
                  编辑
                </el-button>
                <el-button
                  type="danger"
                  link
                  icon="Delete"
                  @click="handleDelete(rule)"
                >
                  删除
                </el-button>
              </div>

              <AggregateColumns status="show" :columns="rule.fieldList" />
            </div>
          </template>
          <template v-else>
            <div class="w-full p-4 text-center text-gray-500">暂无聚合规则</div>
          </template>
        </div>
      </div>

      <template #footer>
        <span class="footer">
          <el-button
            :loading="confirmLoading"
            type="primary"
            @click="handleConfirm"
          >
            确认
          </el-button>
        </span>
      </template>
    </el-dialog>

    <UpdateDialog
      v-model="updateDialog.visible"
      v-bind="updateDialog.params"
      @close="updateDialog.onClose"
    />
  </div>
</template>

<style lang="scss" scoped>
.dialog-content {
  max-height: 60vh; // 设置最大高度为视口高度的70%
  padding: 20px;
  overflow-y: auto; // 添加垂直滚动条
}

.table-title {
  color: var(--el-color-primary);
}
</style>
