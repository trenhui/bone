<script setup lang="ts">
import LiabilityAPI, { PlanDTO } from "@/api/liability";
import {
  validateChineseLetterNumberUnderline,
  validateLetterNumberUnderline,
} from "@/utils/strUtils";
defineOptions({
  name: "CreatePlanDialog",
});

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{ policyNo: string }>();

const planList = ref<PlanDTO[]>([
  {
    id: "",
    policyNo: props.policyNo,
    planName: "",
    planCode: "",
    planLimit: -1,
    limitType: -1,
    isEditing: true,
  },
]);
const confirmLoading = ref(false);
const isNeedRefresh = ref(false);

const handleClose = () => {
  dialogVisible.value = false;
  planList.value = [
    {
      id: "",
      policyNo: props.policyNo,
      planName: "",
      planCode: "",
      planLimit: -1,
      limitType: -1,
      isEditing: true,
    },
  ];
  confirmLoading.value = false;
  emits("close", isNeedRefresh.value);
  isNeedRefresh.value = false;
};

const handleAdding = () => {
  planList.value.push({
    id: "",
    policyNo: props.policyNo,
    planName: "",
    planCode: "",
    planLimit: -1,
    limitType: -1,
    isEditing: true,
  });
};

const handleAdd = (row: PlanDTO) => {
  if (!row.planName || !row.planCode) {
    ElMessage.warning("请填写完整后确认");
    return;
  }

  if (
    !row.planName ||
    !validateChineseLetterNumberUnderline(row.planName, 2, 20)
  ) {
    ElMessage.warning("请正确输入计划名称，2-20字，中文字母数字下划线");
    return;
  }

  if (
    planList.value.some(
      (item: PlanDTO) => item != row && item.planName === row.planName
    )
  ) {
    ElMessage.warning("同保单不允许有重复的计划名称，请修改");
    return;
  }

  if (row.planCode && !validateLetterNumberUnderline(row.planCode, 1, 20)) {
    ElMessage.warning("请正确输入计划code，字母数字下划线");
    return;
  }

  if (row.limitType === -1) {
    row.planLimit = -1;
  }

  if (row.limitType !== -1 && row.planLimit <= 0) {
    ElMessage.warning("请正确输入计划保额");
    return;
  }
  row.isEditing = false;
};

const handleCancel = (index: number) => {
  planList.value.splice(index, 1);
};

const handleDelete = (row: PlanDTO) => {
  ElMessageBox.confirm("确定删除该行？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    center: true,
  })
    .then(() => {
      planList.value = planList.value.filter((item: PlanDTO) => item !== row);
    })
    .catch(() => {});
};

const handleLimitChange = (row: PlanDTO, value: number | undefined) => {
  row.planLimit = value || 0;
};

const handleConfirm = async () => {
  try {
    confirmLoading.value = true;

    const hasEditingRow = planList.value.some((row: PlanDTO) => row.isEditing);
    if (hasEditingRow) {
      ElMessage.warning("请先完成正在编辑的行");
      confirmLoading.value = false;
      return;
    }

    //保存的网络请求
    await LiabilityAPI.createPlan(planList.value);

    ElMessage.success("保存成功");
    isNeedRefresh.value = true;
    handleClose();
  } catch (error) {
    console.log("error", error);
  } finally {
    confirmLoading.value = false;
  }
};
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    :before-close="handleClose"
    append-to-body
    :close-on-click-modal="false"
    title="新增计划"
    width="55%"
  >
    <div class="dialog-content">
      <el-table border :data="planList">
        <el-table-column label="计划名称" align="center">
          <template #default="{ row }">
            <el-input
              v-if="row.isEditing"
              v-model="row.planName"
              minlength="2"
              maxlength="20"
            />
            <span v-else>{{ row.planName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="计划Code" align="center">
          <template #default="{ row }">
            <el-input v-if="row.isEditing" v-model="row.planCode" />
            <span v-else>{{ row.planCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="计划保额" align="center">
          <template #default="{ row }">
            <el-row v-if="row.isEditing" :gutter="5">
              <el-col :span="10">
                <el-select v-model="row.limitType">
                  <el-option label="有限额" :value="1" />
                  <el-option label="无限额" :value="-1" />
                </el-select>
              </el-col>
              <el-col :span="14" v-if="row.limitType === 1">
                <el-input-number
                  v-model="row.planLimit"
                  :min="0"
                  :precision="2"
                  @change="(value) => handleLimitChange(row, value)"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-col>
            </el-row>
            <span v-else>
              {{
                row.planLimit === -1
                  ? "无限额"
                  : `有限额，限额：${row.planLimit}`
              }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="150px">
          <template #default="scope">
            <div v-if="scope.row.isEditing">
              <el-button
                type="success"
                icon="Check"
                link
                @click="handleAdd(scope.row)"
              >
                确认
              </el-button>
              <el-button
                type="warning"
                icon="Close"
                link
                @click="handleCancel(scope.$index)"
              >
                取消
              </el-button>
            </div>

            <el-button
              v-else
              type="danger"
              icon="Delete"
              link
              @click="handleDelete(scope.row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-button class="mt-2" link type="primary" @click="handleAdding">
        新增计划
      </el-button>
    </div>

    <template #footer>
      <span class="footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleConfirm">确认</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.dialog-content {
  max-height: 50vh;
  padding: 20px;
  overflow-y: auto;
}
</style>
