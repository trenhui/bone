<script setup lang="ts">
import CertificateConfigAPI, {
  CertificateConfigOptions,
  ICertificateConfig,
} from "@/api/policyConfig/certificateConfig";
defineOptions({
  name: "CertificateConfig",
});

const props = defineProps<{
  insuranceName: string;
  policyNo: string;
}>();

const isEdit = ref(false);
const certificateConfig = ref<ICertificateConfig>({
  createApplication: false,
  applicationConfig: {
    claimAuditType: 0,
    certificateTemplate: "",
    claimProcessNode: "",
    adjustmentConclusion: "",
    imageType: "",
    imageTypePK: "",
  },
  createNotification: false,
  notificationConfig: {
    claimAuditType: 0,
    certificateTemplate: "",
    claimProcessNode: "",
    adjustmentConclusion: "",
    imageType: "",
    imageTypePK: "",
  },
});

const ApplicationOptions = ref<CertificateConfigOptions>({
  certificateTemplate: [],
  claimAuditType: [],
  claimProcessNode: [],
  adjustmentConclusion: [],
  imageType: [],
});

const NotificationOptions = ref<CertificateConfigOptions>({
  certificateTemplate: [],
  claimAuditType: [],
  claimProcessNode: [],
  adjustmentConclusion: [],
  imageType: [],
});

const getApplicationOptions = async () => {
  const res = await CertificateConfigAPI.getOptions(
    props.insuranceName,
    "理赔申请书"
  );
  ApplicationOptions.value = res;
};

const getNotificationOptions = async () => {
  const res = await CertificateConfigAPI.getOptions(
    props.insuranceName,
    "理赔通知书"
  );
  NotificationOptions.value = res;
};

const getCertificateConfig = async () => {
  const res = await CertificateConfigAPI.getCertificateConfigByPolicyNo(
    props.policyNo
  );
  certificateConfig.value = res;
};

onMounted(async () => {
  getApplicationOptions();
  getNotificationOptions();
  getCertificateConfig();
});

const handleApplicationImageTypeChange = (value: string) => {
  const item = ApplicationOptions.value.imageType.find(
    (item) => item.imageType === value
  );
  if (item) {
    certificateConfig.value.applicationConfig.imageTypePK = item.imageTypePK;
  }
};

const handleNotificationImageTypeChange = (value: string) => {
  const item = NotificationOptions.value.imageType.find(
    (item) => item.imageType === value
  );
  if (item) {
    certificateConfig.value.notificationConfig.imageTypePK = item.imageTypePK;
  }
};

const handleEdit = () => {
  isEdit.value = true;
};

const handleCancel = () => {
  isEdit.value = false;
  getCertificateConfig();
};

const saveLoading = ref(false);
const handleSave = async () => {
  try {
    saveLoading.value = true;
    await CertificateConfigAPI.saveCertificateConfig({
      policyNo: props.policyNo,
      config: certificateConfig.value,
    });
    ElMessage.success("保存成功");
    isEdit.value = false;
    getCertificateConfig();
  } catch (error) {
    console.error(error);
  } finally {
    saveLoading.value = false;
  }
};
</script>

<template>
  <div class="p-4 border border-[var(--el-border-color)]">
    <p class="text-16px font-bold">单证配置</p>
    <el-form :disabled="!isEdit">
      <el-form-item label="理赔申请书">
        <el-radio-group v-model="certificateConfig.createApplication">
          <el-radio :value="false">无需生成</el-radio>
          <el-radio :value="true">需生成</el-radio>
        </el-radio-group>
        <el-table v-if="certificateConfig.createApplication" :data="[1]" border>
          <el-table-column
            label="适用赔案"
            prop="claimAuditType"
            align="center"
          >
            <template #default>
              <el-select
                v-model="certificateConfig.applicationConfig.claimAuditType"
                placeholder="请选择"
              >
                <el-option
                  v-for="item in ApplicationOptions.claimAuditType"
                  :key="item.code"
                  :label="item.desc"
                  :value="item.code"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column
            label="生成节点"
            prop="claimProcessNode"
            align="center"
          >
            <template #default>
              <el-select
                v-model="certificateConfig.applicationConfig.claimProcessNode"
                placeholder="请选择"
              >
                <el-option
                  v-for="item in ApplicationOptions.claimProcessNode"
                  :key="item.code"
                  :label="item.desc"
                  :value="item.code"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column
            label="生成条件（理赔结论）"
            prop="adjustmentConclusion"
            align="center"
          >
            <template #default>
              <el-select
                v-model="
                  certificateConfig.applicationConfig.adjustmentConclusion
                "
                placeholder="请选择"
              >
                <el-option
                  v-for="item in ApplicationOptions.adjustmentConclusion"
                  :key="item.code"
                  :label="item.desc"
                  :value="item.code"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column
            label="选择模版"
            prop="certificateTemplate"
            align="center"
          >
            <template #default>
              <el-select
                v-model="
                  certificateConfig.applicationConfig.certificateTemplate
                "
                placeholder="请选择"
              >
                <el-option
                  v-for="item in ApplicationOptions.certificateTemplate"
                  :key="item.code"
                  :label="item.desc"
                  :value="item.code"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="所属影像分类" prop="imageType" align="center">
            <template #default>
              <el-select
                v-model="certificateConfig.applicationConfig.imageType"
                placeholder="请选择"
                @change="handleApplicationImageTypeChange"
              >
                <el-option
                  v-for="item in ApplicationOptions.imageType"
                  :key="item.imageType"
                  :label="item.desc"
                  :value="item.imageType"
                />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
      </el-form-item>
      <el-form-item label="理赔通知单">
        <el-radio-group v-model="certificateConfig.createNotification">
          <el-radio :value="false">无需生成</el-radio>
          <el-radio :value="true">需生成</el-radio>
        </el-radio-group>
        <el-table
          v-if="certificateConfig.createNotification"
          :data="[1]"
          border
        >
          <el-table-column
            label="适用赔案"
            prop="claimAuditType"
            align="center"
          >
            <template #default>
              <el-select
                v-model="certificateConfig.notificationConfig.claimAuditType"
                placeholder="请选择"
              >
                <el-option
                  v-for="item in NotificationOptions.claimAuditType"
                  :key="item.code"
                  :label="item.desc"
                  :value="item.code"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column
            label="生成节点"
            prop="claimProcessNode"
            align="center"
          >
            <template #default>
              <el-select
                v-model="certificateConfig.notificationConfig.claimProcessNode"
                placeholder="请选择"
              >
                <el-option
                  v-for="item in NotificationOptions.claimProcessNode"
                  :key="item.code"
                  :label="item.desc"
                  :value="item.code"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column
            label="生成条件（理赔结论）"
            prop="adjustmentConclusion"
            align="center"
          >
            <template #default>
              <el-select
                v-model="
                  certificateConfig.notificationConfig.adjustmentConclusion
                "
                placeholder="请选择"
              >
                <el-option
                  v-for="item in NotificationOptions.adjustmentConclusion"
                  :key="item.code"
                  :label="item.desc"
                  :value="item.code"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column
            label="选择模版"
            prop="certificateTemplate"
            align="center"
          >
            <template #default>
              <el-select
                v-model="
                  certificateConfig.notificationConfig.certificateTemplate
                "
                placeholder="请选择"
              >
                <el-option
                  v-for="item in NotificationOptions.certificateTemplate"
                  :key="item.code"
                  :label="item.desc"
                  :value="item.code"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="所属影像分类" prop="imageType" align="center">
            <template #default>
              <el-select
                v-model="certificateConfig.notificationConfig.imageType"
                placeholder="请选择"
                @change="handleNotificationImageTypeChange"
              >
                <el-option
                  v-for="item in NotificationOptions.imageType"
                  :key="item.imageType"
                  :label="item.desc"
                  :value="item.imageType"
                />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
      </el-form-item>
    </el-form>

    <div class="flex-center mt-4">
      <el-button v-if="!isEdit" type="primary" @click="handleEdit">
        编辑
      </el-button>
      <el-button v-if="isEdit" type="primary" @click="handleCancel">
        取消
      </el-button>
      <el-button
        v-if="isEdit"
        type="primary"
        @click="handleSave"
        :loading="saveLoading"
      >
        保存
      </el-button>
    </div>
  </div>
</template>

<style lang="scss" scoped></style>
