<script setup lang="ts">
import LiabilityAPI, { ShareDTO, PlanDTO } from "@/api/liability";
import { validateRegex } from "@/utils/strUtils";
defineOptions({
  name: "CreateShareCodeDialog",
});

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  policyNo: string;
  planList: PlanDTO[];
}>();

const shareCodeList = ref<ShareDTO[]>([]);
const confirmLoading = ref(false);
const isNeedRefresh = ref(false);

const handleClose = () => {
  dialogVisible.value = false;
  shareCodeList.value = [];
  emits("close", isNeedRefresh.value);
  confirmLoading.value = false;
  isNeedRefresh.value = false;
};

const handleAdding = () => {
  shareCodeList.value.push({
    id: null,
    policyNo: props.policyNo,
    planId: "",
    planName: "",
    shareId: "",
    shareCode: "",
    shareLimit: 0,
    isEditing: true,
  });
};

const getInputValue = (code: string) => {
  return code.startsWith("share_") ? code.slice(6) : code;
};

const handleAdd = (row: ShareDTO) => {
  if (!row.shareCode || !row.planId) {
    ElMessage.warning("请填写完整后确认");
    return;
  }

  // 数字、字母，不超过4位。
  if (!validateRegex(row.shareCode, /^[a-zA-Z0-9]{1,4}$/)) {
    ElMessage.warning("共保代码必须由数字、字母组成，且不超过4位");
    return;
  }

  // 数字，大于0
  if (!validateRegex(row.shareLimit.toString(), /^[0-9]+$/)) {
    ElMessage.warning("共保保额必须为数字，且大于0");
    return;
  }

  //共保代码不能重复
  const hasSameShareCode = shareCodeList.value.some(
    (item) => item !== row && item.shareCode === row.shareCode
  );
  if (hasSameShareCode) {
    ElMessage.warning("在同一保单下的共保代码不可重复");
    return;
  }

  row.shareCode = `share_${row.shareCode}`;
  row.isEditing = false;
};

const handleCancel = (index: number) => {
  shareCodeList.value.splice(index, 1);
};

const handleDelete = (row: any) => {
  ElMessageBox.confirm("确定删除该行？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    center: true,
  })
    .then(() => {
      shareCodeList.value = shareCodeList.value.filter(
        (item: any) => item !== row
      );
    })
    .catch(() => {});
};

const handleConfirm = async () => {
  try {
    if (shareCodeList.value.length === 0) {
      ElMessage.warning("请至少配置一个共保代码");
      return;
    }

    if (shareCodeList.value.some((item: any) => item.isEditing)) {
      ElMessage.warning("请先完成正在编辑的行");
      return;
    }

    confirmLoading.value = true;
    //保存的网络请求
    await LiabilityAPI.createShareCode(shareCodeList.value);
    isNeedRefresh.value = true;

    ElMessage.success("保存成功");
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
    title="新增共保代码"
    width="55%"
  >
    <div class="dialog-content">
      <div class="mb-2">
        同一计划下，当出现跨险种多责任或同一险种下部分多责任情形下的共享保额情形时，可通过本功能配置"共保代码"来实现。
      </div>
      <div class="mb-4">
        即该保单的某一计划配置了同一共保代码的责任共享共保代码的保额，同时受到本责任保额、所属险种保额、所属计划保额的控额。
      </div>
      <el-table border :data="shareCodeList">
        <el-table-column label="计划" align="center">
          <template #default="{ row }">
            <el-select v-if="row.isEditing" v-model="row.planId">
              <el-option
                v-for="plan in props.planList"
                :key="plan.id"
                :label="plan.planName"
                :value="plan.id"
              />
            </el-select>
            <span v-else>
              {{
                props.planList.find((plan: PlanDTO) => plan.id === row.planId)
                  ?.planName
              }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="共保代码" align="center">
          <template #default="{ row }">
            <el-input
              v-if="row.isEditing"
              :model-value="getInputValue(row.shareCode)"
              @update:model-value="(val) => (row.shareCode = val)"
            >
              <template #prefix>share_</template>
            </el-input>
            <span v-else>{{ row.shareCode }}</span>
          </template>
        </el-table-column>
        <el-table-column label="共保保额" align="center">
          <template #default="{ row }">
            <el-input v-if="row.isEditing" v-model.number="row.shareLimit">
              <template #suffix>元</template>
            </el-input>
            <span v-else>{{ row.shareLimit }} 元</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center">
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
        新增共保代码
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
