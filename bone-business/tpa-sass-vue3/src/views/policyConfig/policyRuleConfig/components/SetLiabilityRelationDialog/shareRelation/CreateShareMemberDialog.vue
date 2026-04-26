<script setup lang="ts">
import LiabilityAPI, {
  ShareMemberDTO,
  PlanDTO,
  LiabilityShortDTO,
  ShareDTO,
} from "@/api/liability";
defineOptions({
  name: "CreateShareMemberDialog",
});

const emits = defineEmits(["close"]);
const dialogVisible = defineModel({ type: Boolean, default: false });
const props = defineProps<{
  policyNo: string;
  planList: PlanDTO[];
  liabilityList: LiabilityShortDTO[];
  shareCodeList: ShareDTO[];
}>();

const shareMemberList = ref<ShareMemberDTO[]>([]);
const confirmLoading = ref(false);
const isNeedRefresh = ref(false);

const getSelectableLiabilityList = (planId: string) => {
  // 先选计划，根据计划id过滤责任列表
  if (!planId) {
    return [];
  }

  return props.liabilityList.filter((liability) => liability.planId === planId);
};

const getSelectableShareCodeList = (planId: string) => {
  // 先选计划，根据计划id过滤共保代码列表
  if (!planId) {
    return [];
  }

  return props.shareCodeList.filter((shareCode) => shareCode.planId === planId);
};

const handleClose = () => {
  dialogVisible.value = false;
  shareMemberList.value = [];
  emits("close", isNeedRefresh.value);
  confirmLoading.value = false;
  isNeedRefresh.value = false;
};

const handleAdding = () => {
  shareMemberList.value.push({
    policyNo: props.policyNo,
    planId: "",
    liabilityUuid: "",
    shareCode: "",
    isEditing: true,
  });
};

const handleAdd = (row: ShareMemberDTO) => {
  console.log(row);
  if (!isValid(row)) {
    return;
  }
  row.isEditing = false;
};

const handleCancel = (index: number) => {
  shareMemberList.value.splice(index, 1);
};

const handleDelete = (row: ShareMemberDTO) => {
  ElMessageBox.confirm("确定删除该行？", "提示", {
    confirmButtonText: "确定",
    cancelButtonText: "取消",
    type: "warning",
    center: true,
  })
    .then(() => {
      shareMemberList.value = shareMemberList.value.filter(
        (item: ShareMemberDTO) => item !== row
      );
    })
    .catch(() => {});
};

/**
 * 1、若同一个责任，选择两个以上的共保代码，则提示“一个责任最多参与2个共保代码”；
 * 2、若一个责任已选择了一个共保代码，则提示“该责任不可再次选择该共保代码”；
 * 3、不同计划下的责任，不可选择同一个共保代码。若同一个共保代码被不同计划的责任所选择，提示“不同计划的责任不可设置同一个共保代码，请刷新后再设置”；
 */
const isValid = (row: ShareMemberDTO) => {
  // 基本校验：必填字段不能为空
  if (!row.planId || !row.liabilityUuid || !row.shareCode) {
    ElMessage.warning("请填写完整信息");
    return false;
  }

  // 规则1：若同一个责任，选择两个以上的共保代码，则提示"一个责任最多参与2个共保代码"
  const sameResponsibilityCount = shareMemberList.value.filter(
    (item) => item.liabilityUuid === row.liabilityUuid && !item.isEditing
  ).length;
  if (row.isEditing && sameResponsibilityCount >= 2) {
    ElMessage.warning("一个责任最多参与2个共保代码");
    return false;
  }

  // 规则2：若一个责任已选择了一个共保代码，则提示"该责任不可再次选择该共保代码"
  const hasDuplicateShareCode = shareMemberList.value.some(
    (item) =>
      item !== row &&
      item.liabilityUuid === row.liabilityUuid &&
      item.shareCode === row.shareCode
  );

  if (hasDuplicateShareCode) {
    ElMessage.warning("该责任不可再次选择该共保代码");
    return false;
  }

  // 规则3：不同计划下的责任，不可选择同一个共保代码
  const currentPlanId = row.planId;
  const hasCrossPlansShareCode = shareMemberList.value.some(
    (item) =>
      item !== row &&
      item.planId !== currentPlanId &&
      item.shareCode === row.shareCode
  );

  if (hasCrossPlansShareCode) {
    ElMessage.warning("不同计划的责任不可设置同一个共保代码");
    return false;
  }

  return true;
};

const handleConfirm = async () => {
  try {
    confirmLoading.value = true;

    //保存的网络请求
    await LiabilityAPI.createShareRelation(shareMemberList.value);
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
    title="新增共保责任"
    width="55%"
  >
    <div class="dialog-content">
      <div class="mb-4">选择具有共保关系的责任并配置对应的共保代码</div>
      <el-table border :data="shareMemberList">
        <el-table-column label="计划" align="center">
          <template #default="scope">
            <el-select
              v-model="scope.row.planId"
              placeholder="请选择计划"
              :disabled="!scope.row.isEditing"
            >
              <el-option
                v-for="item in props.planList"
                :key="item.id"
                :label="item.planName"
                :value="item.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="该计划下的责任名称" align="center">
          <template #default="scope">
            <el-select
              v-model="scope.row.liabilityUuid"
              placeholder="请选择责任名称"
              :disabled="!scope.row.isEditing"
            >
              <el-option
                v-for="item in getSelectableLiabilityList(scope.row.planId)"
                :key="item.uuid"
                :label="item.liabilityName"
                :value="item.uuid"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="共保代码" align="center">
          <template #default="scope">
            <el-select
              v-model="scope.row.shareCode"
              placeholder="请选择共保代码"
              :disabled="!scope.row.isEditing"
            >
              <el-option
                v-for="item in getSelectableShareCodeList(scope.row.planId)"
                :key="item.id!"
                :label="item.shareCode"
                :value="item.shareCode"
              />
            </el-select>
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
        新增共保责任
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
