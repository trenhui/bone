<template>
  <el-dialog
    v-model="visible"
    title="重新推送"
    width="30%"
    :close-on-click-modal="false"
  >
    <div>
      <el-form label-width="auto" label-position="left">
        <el-form-item label="重新推送方式">
          <el-radio-group v-model="rePushType" placeholder="请选择重新推送方式">
            <el-radio value="1">常规等待推送（排队或者实时推）</el-radio>
            <el-radio value="2" disabled>强制立即推送（部分业务）</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <div>
        说明:常规等待推送，实时推取决于不同保司业务的接口对接情况，否则为排队定时推送。如永诚业务即通过接口实时推送。
      </div>
    </div>

    <template #footer>
      <el-button @click="cancel">取消</el-button>
      <el-button type="primary" @click="confirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import PushFailAPI from "@/api/claimManage/pushfail";
import type { ClaimPushFail } from "@/api/claimManage/pushfail";
defineOptions({
  name: "RePushDialog",
});

const visible = ref(false);
const rePushType = ref("1");
const selectedRows = ref<ClaimPushFail[]>([]);

const confirmResolve = ref<(v?: unknown) => void>();
const confirmReject = ref<(r?: unknown) => void>();

const open = (rows: ClaimPushFail[]) => {
  visible.value = true;
  selectedRows.value = rows;
  return new Promise((resolve, reject) => {
    confirmResolve.value = resolve;
    confirmReject.value = reject;
  });
};

const confirm = async () => {
  if (selectedRows.value.length === 0) {
    ElMessage.warning("请选择需要重新推送的行");
    return;
  }
  try {
    await PushFailAPI.repush(selectedRows.value.map((item) => item.claimNo));
    ElMessage.success("重新推送成功");
    visible.value = false;
    confirmResolve.value?.();
  } catch (error) {
    console.error(error);
  }
};

const cancel = () => {
  visible.value = false;
  confirmReject.value?.();
};

defineExpose({ open });
</script>
