<template>
  <el-dialog
    v-model="visible"
    title="退回人工处理"
    width="30%"
    :close-on-click-modal="false"
  >
    <div>
      <el-form
        ref="formRef"
        :model="form"
        label-width="auto"
        label-position="left"
        :rules="rules"
      >
        <el-form-item label="请选择退回环节" prop="node">
          <el-select v-model="form.node" placeholder="请选择退回环节" clearable>
            <el-option
              v-for="item in NodeOptions"
              :key="item.label"
              :label="item.label"
              :value="item.label"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="退回至原处理人员" prop="isOriginalUser">
          <el-select
            style="width: 100px; margin-right: 10px"
            v-model="form.isOriginalUser"
            placeholder="请选择退回至原处理人员"
            clearable
          >
            <el-option value="0" label="是" />
            <el-option value="1" label="否" />
          </el-select>
          <el-input
            style="width: calc(100% - 110px)"
            v-model="form.userName"
            clearable
            :maxlength="100"
            placeholder="请输入指定处理人员姓名"
          />
        </el-form-item>
        <el-form-item label="退回类型" prop="returnType">
          <el-select
            v-model="form.returnType"
            placeholder="请选择退回类型"
            clearable
          >
            <el-option
              v-for="item in ReturnTypeOptions"
              :key="item.label"
              :label="item.label"
              :value="item.label"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="退回原因" prop="returnReason">
          <el-input
            v-model="form.returnReason"
            clearable
            :maxlength="100"
            :show-word-limit="true"
            :rows="3"
            type="textarea"
            placeholder="请输入退回原因，大于10个字"
          />
        </el-form-item>
      </el-form>
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
import type { ElForm } from "element-plus";
defineOptions({
  name: "RePushDialog",
});

const visible = ref(false);
const form = ref({
  claimNos: [] as string[],
  node: "",
  userName: "",
  isOriginalUser: "0",
  returnType: "",
  returnReason: "",
});
const formRef = ref<InstanceType<typeof ElForm>>();
const rules = {
  node: [{ required: true, message: "请选择退回环节" }],
  isOriginalUser: [{ required: true, message: "请选择是否退回至原处理人员" }],
  returnType: [{ required: true, message: "请选择退回类型" }],
  returnReason: [
    { required: true, message: "请输入退回原因" },
    { min: 10, message: "退回原因必须大于10个字" },
  ],
};

const NodeOptions = [
  { label: "初审" },
  { label: "录入" },
  { label: "审核" },
  { label: "复核" },
] as const;

const ReturnTypeOptions = [
  { label: "系统问题" },
  { label: "初审错误" },
  { label: "录入错误" },
  { label: "审核错误" },
  { label: "其他问题" },
] as const;

const confirmResolve = ref<(v?: unknown) => void>();
const confirmReject = ref<(r?: unknown) => void>();

const open = (rows: ClaimPushFail[]) => {
  visible.value = true;
  form.value.claimNos = rows.map((item) => item.claimNo);
  return new Promise((resolve, reject) => {
    confirmResolve.value = resolve;
    confirmReject.value = reject;
  });
};

const close = () => {
  visible.value = false;
  form.value = {
    claimNos: [],
    node: "",
    userName: "",
    isOriginalUser: "0",
    returnType: "",
    returnReason: "",
  };
  formRef.value?.resetFields();
};

const confirm = async () => {
  if (form.value.claimNos.length === 0) {
    ElMessage.warning("请选择需要退回人工处理的行");
    return;
  }

  if (formRef.value) {
    const result = await formRef.value.validate();
    if (!result) {
      return;
    }
  }

  if (form.value.isOriginalUser === "1" && !form.value.userName) {
    ElMessage.warning("请输入指定处理人员姓名");
    return;
  }

  form.value.userName =
    form.value.isOriginalUser === "1" ? form.value.userName : "";

  try {
    await PushFailAPI.return(form.value);
    ElMessage.success("退回人工处理成功");
    close();
    confirmResolve.value?.();
  } catch (error) {
    console.error(error);
  }
};

const cancel = () => {
  close();
  confirmReject.value?.();
};

defineExpose({ open });
</script>
