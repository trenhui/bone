<script setup lang="ts">
import LiabilityRule from "./tabs/liabilityRule/index.vue";
import PersonalQuota from "./tabs/personalQuota/index.vue";
import CertificateConfig from "./tabs/certificateConfig/index.vue";
import DataAPI from "@/api/data";
import PolicyConfigAPI from "@/api/policyConfig";
defineOptions({
  name: "PolicyRuleConfig",
});

const route = useRoute();
const policyNo = route.query.policyNo as string;
const id = route.query.id as string;
const info = ref<any>({});

const init = async () => {
  info.value = await DataAPI.getOne({
    id,
    modelNames: ["policy"],
  });
};
onMounted(() => {
  init();
});

const activeTab = ref("liabilityRule");

const handleToggleConfig = async () => {
  try {
    await PolicyConfigAPI.toggleConfig(policyNo);
    ElMessage.success("切换成功");
    init();
  } catch (error) {
    console.error(error);
  }
};

const configStatus = {
  INACTIVE: "未启用",
  ACTIVE: "启用中",
  PAUSE: "停用中",
};
const toggleConfigLabel = {
  INACTIVE: "启用保单责任",
  ACTIVE: "停用保单责任",
  PAUSE: "启用保单责任",
};
const getConfigStatusLabel = (value: string) => {
  return configStatus[value as keyof typeof configStatus] || "未启用";
};
const getToggleConfigLabel = (value: string) => {
  return (
    toggleConfigLabel[value as keyof typeof toggleConfigLabel] || "启用保单责任"
  );
};
</script>

<template>
  <div class="app-container">
    <el-card class="mb-2" shadow="never">
      <span class="font-bold text-[var(--el-text-color-primary)]">
        保单规则配置
      </span>
    </el-card>

    <el-card shadow="never">
      <!-- 保单信息 -->
      <div>
        <div class="flex justify-between items-center">
          <div class="flex-basis-[80%]">
            <el-descriptions :column="3">
              <el-descriptions-item label="普康保单号">
                {{ policyNo }}
              </el-descriptions-item>
              <el-descriptions-item label="生效期间">
                {{ info?.main?.effdate }} - {{ info?.main?.expdate }}
              </el-descriptions-item>
              <el-descriptions-item label="保单状态">
                {{ info?.main?.status }}
              </el-descriptions-item>
              <el-descriptions-item label="投保公司">
                {{ info?.main?.insureName }}
              </el-descriptions-item>
              <el-descriptions-item label="保险分公司">
                {{ info?.main?.insuranceName }}
              </el-descriptions-item>
            </el-descriptions>
          </div>

          <div class="flex-basis-[30%] flex items-center justify-end">
            <span class="item-label">保单规则：</span>
            <span class="item-value">
              {{ getConfigStatusLabel(info?.main?.configStatus) }}
            </span>
            <el-button class="ml-4" type="warning" @click="handleToggleConfig">
              {{ getToggleConfigLabel(info?.main?.configStatus) }}
            </el-button>
          </div>
        </div>
      </div>

      <!-- 保单规则配置 -->
      <div class="relative">
        <el-tabs v-model="activeTab" type="border-card">
          <el-tab-pane label="责任规则" name="liabilityRule" lazy>
            <liability-rule :policy-no="policyNo" />
          </el-tab-pane>
          <el-tab-pane label="个人专属额度" name="personalQuota" lazy>
            <personal-quota :policy-no="policyNo" :tenant-id="info.tenantId" />
          </el-tab-pane>
          <el-tab-pane disabled label="医院库" name="hospitalLib" lazy>
            <div class="mx-5">
              <span class="item-label">保单规则配置</span>
            </div>
          </el-tab-pane>
          <el-tab-pane disabled label="疾病库" name="diseaseLib" lazy>
            <div class="mx-5">
              <span class="item-label">保单规则配置</span>
            </div>
          </el-tab-pane>
          <el-tab-pane disabled label="药品库" name="drugLib" lazy>
            <div class="mx-5">
              <span class="item-label">保单规则配置</span>
            </div>
          </el-tab-pane>
          <el-tab-pane disabled label="费用项目规则" name="costRule" lazy>
            <div class="mx-5">
              <span class="item-label">保单规则配置</span>
            </div>
          </el-tab-pane>
          <el-tab-pane disabled label="职业列表" name="careerList" lazy>
            <div class="mx-5">
              <span class="item-label">保单规则配置</span>
            </div>
          </el-tab-pane>
          <el-tab-pane label="作业配置" name="certificateConfig" lazy>
            <certificate-config
              :insurance-name="info?.main?.insuranceName"
              :policy-no="policyNo"
            />
          </el-tab-pane>
        </el-tabs>

        <div class="absolute-rt h-40px flex items-center mr-4">
          <el-button link type="primary">责任编辑记录</el-button>
          <el-button link type="primary">理算演示</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style lang="scss" scoped>
.grid-layout {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.grid-item {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.item-label {
  font-size: 14px;
  font-weight: bold;
  color: var(--el-text-color-regular);
}

.item-value {
  margin-left: 12px;
  font-size: 14px;
  color: var(--el-text-color-regular);
}
</style>
