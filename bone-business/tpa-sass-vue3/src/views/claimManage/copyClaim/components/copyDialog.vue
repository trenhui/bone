<script setup lang="ts">
import CopyClaimAPI from "@/api/claimManage/copyClaim";
import type { CopyClaimParams } from "@/api/claimManage/copyClaim";
import type { ElForm } from "element-plus";
defineOptions({
  name: "RePushDialog",
});

const visible = ref(false);
const form = ref<CopyClaimParams>({
  claimNos: [] as string[],
  isBatchAndSignTime: false,
  isCompensationAmount: false,
  isCopySerialNo: false,
  isCopyInsureClaimNo: false,
  newClaimStatus: "",
  remark: "",
  newClaimOperator: "",
  operatorName: "",
});
const formRef = ref<InstanceType<typeof ElForm>>();
const rules = {
  claimNos: [{ required: true, message: "请输入原赔案号" }],
  isBatchAndSignTime: [
    { required: true, message: "请选择是否生成新批次和签收时间" },
  ],
  isCompensationAmount: [
    { required: true, message: "请选择原赔案赔付金额是否转至新赔案的三方已赔" },
  ],
  isCopySerialNo: [
    { required: true, message: "请选择原配案收单流水号是否复制" },
  ],
  isCopyInsureClaimNo: [
    { required: true, message: "请选择原配案保司报案号是否复制" },
  ],
  newClaimStatus: [{ required: true, message: "请选择新赔案所处环节" }],
  newClaimOperator: [
    { required: true, message: "请选择处理人员" },
    {
      validator: (rule: any, value: any, callback: any) => {
        if (value === "SPECIFIC") {
          if (!form.value.operatorName) {
            return callback(new Error("请输入指定处理人员姓名"));
          }
        }
        callback();
      },
    },
  ],
  remark: [{ required: true, message: "请输入备注" }],
};
const loading = ref(false);

const OperatorTypeOptions = [
  { label: "原赔案人员", value: "ORIGIN" },
  { label: "操作人员本人", value: "CURRENT" },
  { label: "指定人员", value: "SPECIFIC" },
] as const;

const confirmResolve = ref<(v?: unknown) => void>();
const confirmReject = ref<(r?: unknown) => void>();

const open = () => {
  visible.value = true;
  return new Promise((resolve, reject) => {
    confirmResolve.value = resolve;
    confirmReject.value = reject;
  });
};

const close = () => {
  visible.value = false;
  form.value = {
    claimNos: [],
    isBatchAndSignTime: false,
    isCompensationAmount: false,
    isCopySerialNo: false,
    isCopyInsureClaimNo: false,
    newClaimStatus: "",
    remark: "",
    newClaimOperator: "",
    operatorName: "",
  };
  formRef.value?.resetFields();
};

const confirm = async () => {
  if (form.value.claimNos.length === 0) {
    ElMessage.warning("请输入原赔案号");
    return;
  }

  if (formRef.value) {
    const result = await formRef.value.validate();
    if (!result) {
      return;
    }
  }

  try {
    loading.value = true;
    await CopyClaimAPI.copyClaim(form.value);
    ElMessage.success("复制赔案成功");
    close();
    confirmResolve.value?.();
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
};

const cancel = () => {
  close();
  confirmReject.value?.();
};

defineExpose({ open });
</script>

<template>
  <el-dialog
    v-model="visible"
    title="复制赔案saas"
    width="35%"
    :close-on-click-modal="false"
    :before-close="close"
    top="5vh"
  >
    <div class="p-2">
      <el-form
        ref="formRef"
        :model="form"
        label-width="auto"
        label-position="left"
        :rules="rules"
      >
        <el-form-item label="原赔案号" prop="claimNos">
          <el-input
            v-model="form.claimNos as any"
            type="textarea"
            placeholder="支持多个赔案复制，一行一个赔案号"
            clearable
            :rows="3"
          />
        </el-form-item>
        <p>
          默认将复制当前原赔案的相关信息至新赔案，包括：
          赔案基本信息（出险人信息、领款人信息、受益人信息、签收信息）；
          赔案发票信息（赔案影像、发票信息、发票关联影像信息）；
          客户报案信息（含客户签名影像）和出险信息。
        </p>
        <div>请确认以下选项：</div>
        <el-form-item label="生成新批次和签收时间" prop="isBatchAndSignTime">
          <el-select
            v-model="form.isBatchAndSignTime"
            placeholder="请选择"
            clearable
          >
            <el-option :value="true" label="新生成" />
            <el-option :value="false" label="同原赔案" />
          </el-select>
        </el-form-item>
        <el-form-item label="原赔案赔付金额" prop="isCompensationAmount">
          <el-select
            v-model="form.isCompensationAmount"
            placeholder="请选择"
            clearable
          >
            <el-option :value="true" label="转至新赔案的三方已赔" />
            <el-option :value="false" label="手动处理" />
          </el-select>
        </el-form-item>
        <el-form-item label="原赔案收单流水号" prop="isCopySerialNo">
          <el-select
            v-model="form.isCopySerialNo"
            placeholder="请选择"
            clearable
          >
            <el-option :value="true" label="复制至新赔案" />
            <el-option :value="false" label="不复制" />
          </el-select>
        </el-form-item>
        <el-form-item label="原赔案保司报案号" prop="isCopyInsureClaimNo">
          <el-select
            v-model="form.isCopyInsureClaimNo"
            placeholder="请选择"
            clearable
          >
            <el-option :value="true" label="复制至新赔案" />
            <el-option :value="false" label="不复制" />
          </el-select>
        </el-form-item>
        <el-form-item label="新赔案所处环节" prop="newClaimStatus">
          <el-select
            v-model="form.newClaimStatus"
            placeholder="请选择"
            clearable
          >
            <el-option :value="41" label="审核中" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理人员" prop="newClaimOperator">
          <el-select
            v-model="form.newClaimOperator"
            placeholder="请选择"
            clearable
          >
            <el-option
              v-for="item in OperatorTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>

          <el-input
            v-if="form.newClaimOperator === 'SPECIFIC'"
            class="mt-1"
            v-model="form.operatorName"
            placeholder="请输入指定处理人员姓名"
            clearable
          />
        </el-form-item>
        <el-form-item label="复制备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            placeholder="请输入备注"
            clearable
          />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="cancel">取消</el-button>
      <el-button type="primary" @click="confirm" :loading="loading">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
p {
  margin-bottom: 10px;
  font-size: 14px;
  color: #606266;
}
</style>
