<script setup lang="ts">
import { defineAsyncComponent, defineComponent, h } from "vue";
import PublishDialog from "@/components/config/PublishDialog/index.vue";
import { ProcessTypeEnum } from "@/enums/process/ProcessTypeEnum";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import { PageCodeEnum } from "@/enums/PageCodeEnum";
import {
  getBizIdentityTypeLabel,
  BizIdentityTypeEnum,
} from "@/enums/BizIdentityTypeEnum";
import ModelAPI from "@/api/model";
import PageAPI from "@/api/page";

const route = useRoute();
const router = useRouter();

const {
  code: bizIdentityCode,
  name: bizIdentityName,
  type: bizIdentityType,
} = route.query;

// 优化异步组件配置
const asyncComponentOptions = {
  timeout: 3000,
  onError: (
    error: Error,
    retry: () => void,
    fail: () => void,
    attempts: number
  ) => {
    if (attempts <= 3) {
      retry();
    } else {
      fail();
    }
  },
};

const Precheck = defineAsyncComponent({
  loader: () => import("./precheck/index.vue"),
  ...asyncComponentOptions,
});
const Entry = defineAsyncComponent({
  loader: () => import("./entry/index.vue"),
  ...asyncComponentOptions,
});
const QualityCheck = defineAsyncComponent({
  loader: () => import("./qualityCheck/index.vue"),
  ...asyncComponentOptions,
});
const Audit = defineAsyncComponent({
  loader: () => import("./audit/index.vue"),
  ...asyncComponentOptions,
});
const Review = defineAsyncComponent({
  loader: () => import("./review/index.vue"),
  ...asyncComponentOptions,
});
const OptionSet = defineAsyncComponent({
  loader: () => import("@/components/config/CommonOptionConfig/index.vue"),
  ...asyncComponentOptions,
});
const ExclusiveField = defineAsyncComponent({
  loader: () => import("./exclusiveField/index.vue"),
  ...asyncComponentOptions,
});
const BackendRule = defineAsyncComponent({
  loader: () => import("./backendRule/index.vue"),
  ...asyncComponentOptions,
});
const TPAFlow = defineAsyncComponent({
  loader: () => import("@/components/config/TPAFlowConfig/index.vue"),
  ...asyncComponentOptions,
});

defineOptions({
  name: "BizIdentityConfig",
});

const currentTabPane = ref<ProcessTypeEnum | string>("option");

const handlePreview = (type: PageCodeEnum) => {
  const route = router.resolve({
    name: "ClaimDetail",
    query: {
      pageCode: type,
      bizIdentityCode,
      displayMode: DisplayModeEnum.PREVIEW,
    },
  });

  window.open(route.href, "_blank");
};
const handleEditRecord = () => {
  console.log("编辑记录");
};

const closePublishDialog = () => {
  publishDialog.value.visible = false;
};
const publishDialog = ref({
  visible: false,
  onClose: closePublishDialog,
});
const handlePublish = () => {
  publishDialog.value.visible = true;
};

const syncRuleLoading = ref(false);
const handleSyncRule = () => {
  ElMessageBox.confirm(
    `主体名称：${bizIdentityName} <br/><br/>正在同步标准作业的新增规则，请确认。<br/>说明:同步规则默认为不启用。此前当前已有规则不受影响。`,
    "同步标准规则",
    {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      dangerouslyUseHTMLString: true,
    }
  ).then(async () => {
    try {
      syncRuleLoading.value = true;
      await PageAPI.pullRulesFromBase(bizIdentityCode as string);
      ElMessage.success("同步标准规则成功");
    } catch (error) {
      console.error(error);
    } finally {
      syncRuleLoading.value = false;
    }
  });
};

const syncFieldLoading = ref(false);
const handleSyncField = async () => {
  ElMessageBox.confirm(
    `主体名称：${bizIdentityName} <br/><br/>正在同步标准作业的新增字段，请确认。<br/>说明:同步字段默认为不启用。此前当前已有字段不受影响。`,
    "同步标准字段",
    {
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      dangerouslyUseHTMLString: true,
    }
  ).then(async () => {
    try {
      syncFieldLoading.value = true;
      await ModelAPI.syncBaseTemplateField(bizIdentityCode as string);
      ElMessage.success("同步标准字段成功");
    } catch (error) {
      console.error(error);
    } finally {
      syncFieldLoading.value = false;
    }
  });
};

onMounted(() => {
  import("./precheck/index.vue");
  import("./entry/index.vue");
  import("./qualityCheck/index.vue");
  import("./audit/index.vue");
  import("./review/index.vue");
});
</script>

<template>
  <div class="app-container">
    <el-card class="mb-2" shadow="never">
      <div class="flex justify-between items-center">
        <div class="flex flex-col gap-2">
          <div class="font-bold text-[var(--el-text-color-primary)]">
            主体配置
          </div>
          <div class="text-sm font-normal text-gray-500">
            签收来源为"新TPA"的，仅适用于录入质检审核详情页面及规则的配置，其他环节及列表的配置不生效。
          </div>
        </div>

        <div>
          <el-button-group>
            <el-button type="default" @click="handleEditRecord">
              编辑记录
            </el-button>
            <el-popover
              title="选择预览页面"
              placement="bottom"
              :width="300"
              trigger="click"
            >
              <template #reference>
                <el-button type="success">预览</el-button>
              </template>
              <div class="flex flex-col gap-2">
                <span class="text-sm text-gray-500">
                  预览最新的（含编辑未发布）的字段、规则
                </span>
                <div class="flex flex-col gap-2">
                  <div
                    class="flex items-center justify-between bg-[var(--el-fill-color)] rounded-4px p-2"
                  >
                    录入环节
                    <el-button
                      type="primary"
                      link
                      @click="handlePreview(PageCodeEnum.entry)"
                    >
                      预览
                    </el-button>
                  </div>
                  <div
                    class="flex items-center justify-between bg-[var(--el-fill-color)] rounded-4px p-2"
                  >
                    质检环节
                    <el-button
                      type="primary"
                      link
                      @click="handlePreview(PageCodeEnum.qualitycheck)"
                    >
                      预览
                    </el-button>
                  </div>
                </div>
              </div>
            </el-popover>
            <el-button type="primary" @click="handlePublish">发布</el-button>
          </el-button-group>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <div class="mb-4">
        <div class="mb-4 font-bold">基本信息</div>
        <div class="flex items-center justify-between">
          <div class="flex items-center">
            <div class="flex items-center">
              <div class="w-[120px] text-gray-500">业务主体类型：</div>
              <div>
                {{
                  getBizIdentityTypeLabel(
                    bizIdentityType as unknown as BizIdentityTypeEnum
                  )
                }}
              </div>
            </div>
            <div class="flex items-center ml-10">
              <div class="w-[80px] text-gray-500">主体名称：</div>
              <div>
                {{ bizIdentityName }}
              </div>
            </div>
          </div>
          <div class="flex items-center">
            <div class="text-xs text-yellow-500 mr-2">
              <span v-if="syncFieldLoading">正在同步标准字段，请稍后…</span>
              <span v-if="syncRuleLoading">正在同步标准规则，请稍后…</span>
            </div>
            <el-button
              @click="handleSyncRule"
              :loading="syncRuleLoading"
              :disabled="syncFieldLoading || syncRuleLoading"
            >
              同步标准规则
            </el-button>
            <el-button
              @click="handleSyncField"
              :loading="syncFieldLoading"
              :disabled="syncFieldLoading || syncRuleLoading"
            >
              同步标准字段
            </el-button>
          </div>
        </div>
      </div>
      <el-tabs type="border-card" v-model="currentTabPane">
        <el-tab-pane lazy label="字段选项集" name="option">
          <suspense>
            <option-set
              v-if="currentTabPane === 'option'"
              :biz-identity-code="bizIdentityCode as string"
            />
            <template #fallback>
              <el-skeleton :rows="3" animated />
            </template>
          </suspense>
        </el-tab-pane>
        <el-tab-pane lazy label="TPA流程配置" name="tpaFlow">
          <suspense>
            <TPAFlow
              v-if="currentTabPane === 'tpaFlow'"
              :biz-identity-code="bizIdentityCode as string"
            />
            <template #fallback>
              <el-skeleton :rows="3" animated />
            </template>
          </suspense>
        </el-tab-pane>
        <el-tab-pane lazy label="专属字段" name="exclusiveField">
          <suspense>
            <exclusive-field
              v-if="currentTabPane === 'exclusiveField'"
              :biz-identity-code="bizIdentityCode as string"
            />
            <template #fallback>
              <el-skeleton :rows="3" animated />
            </template>
          </suspense>
        </el-tab-pane>
        <el-tab-pane lazy label="后台处理订阅" name="backendRule">
          <suspense>
            <template #default>
              <backend-rule
                v-if="currentTabPane === 'backendRule'"
                :biz-identity-code="bizIdentityCode as string"
              />
            </template>
            <template #fallback>
              <el-skeleton :rows="3" animated />
            </template>
          </suspense>
        </el-tab-pane>
        <el-tab-pane lazy label="初审环节" :name="ProcessTypeEnum.PRECHECK">
          <suspense>
            <precheck
              v-if="currentTabPane === ProcessTypeEnum.PRECHECK"
              :biz-identity-code="bizIdentityCode as string"
            />
            <template #fallback>
              <el-skeleton :rows="3" animated />
            </template>
          </suspense>
        </el-tab-pane>
        <el-tab-pane lazy label="录入环节" :name="ProcessTypeEnum.ENTRY">
          <suspense>
            <entry
              v-if="currentTabPane === ProcessTypeEnum.ENTRY"
              :biz-identity-code="bizIdentityCode as string"
            />
            <template #fallback>
              <el-skeleton :rows="3" animated />
            </template>
          </suspense>
        </el-tab-pane>
        <el-tab-pane
          lazy
          label="质检环节"
          :name="ProcessTypeEnum.QUALITY_CHECK"
        >
          <suspense>
            <quality-check
              v-if="currentTabPane === ProcessTypeEnum.QUALITY_CHECK"
              :biz-identity-code="bizIdentityCode as string"
            />
            <template #fallback>
              <el-skeleton :rows="3" animated />
            </template>
          </suspense>
        </el-tab-pane>
        <el-tab-pane lazy label="审核环节" :name="ProcessTypeEnum.AUDIT">
          <suspense>
            <audit
              v-if="currentTabPane === ProcessTypeEnum.AUDIT"
              :biz-identity-code="bizIdentityCode as string"
            />
            <template #fallback>
              <el-skeleton :rows="3" animated />
            </template>
          </suspense>
        </el-tab-pane>
        <el-tab-pane lazy label="复核环节" :name="ProcessTypeEnum.REVIEW">
          <suspense>
            <review
              v-if="currentTabPane === ProcessTypeEnum.REVIEW"
              :biz-identity-code="bizIdentityCode as string"
            />
            <template #fallback>
              <el-skeleton :rows="3" animated />
            </template>
          </suspense>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <publish-dialog
      v-model="publishDialog.visible"
      :biz-identity-code="bizIdentityCode as string"
      @close="publishDialog.onClose"
    />
  </div>
</template>

<style lang="scss" scoped>
.loading-container {
  padding: 20px;
}

.error-container {
  padding: 40px;
  text-align: center;
}
</style>
