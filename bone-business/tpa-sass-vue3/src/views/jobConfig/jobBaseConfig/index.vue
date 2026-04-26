<script setup lang="ts">
import { defineAsyncComponent } from "vue";
import PublishDialog from "@/components/config/PublishDialog/index.vue";
import { ProcessTypeEnum } from "@/enums/process/ProcessTypeEnum";
import { DisplayModeEnum } from "@/enums/DisplayModeEnum";
import { PageCodeEnum } from "@/enums/PageCodeEnum";

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

// 使用动态导入
const Sign = defineAsyncComponent({
  loader: () => import("./sign/index.vue"),
  ...asyncComponentOptions,
});
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
const JobManage = defineAsyncComponent({
  loader: () => import("./jobManage/index.vue"),
  ...asyncComponentOptions,
});
const Upload = defineAsyncComponent({
  loader: () => import("./upload/index.vue"),
  ...asyncComponentOptions,
});
const OptionSet = defineAsyncComponent({
  loader: () => import("@/components/config/CommonOptionConfig/index.vue"),
  ...asyncComponentOptions,
});

defineOptions({
  name: "JobBaseConfig",
});

const router = useRouter();

const currentTabPane = ref<ProcessTypeEnum | string>("option");

const handlePreview = (type: PageCodeEnum) => {
  const route = router.resolve({
    name: "ClaimDetail",
    query: {
      pageCode: type,
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

onMounted(() => {
  import("./sign/index.vue");
  import("./precheck/index.vue");
  import("./entry/index.vue");
  import("./qualityCheck/index.vue");
  import("./audit/index.vue");
  import("./review/index.vue");
  import("./jobManage/index.vue");
});
</script>

<template>
  <div class="app-container">
    <el-card class="mb-2" shadow="never">
      <div class="flex justify-between items-center">
        <div class="font-bold text-[var(--el-text-color-primary)]">
          标准作业配置
        </div>
        <div>
          <el-button-group>
            <el-button type="default" disabled @click="handleEditRecord">
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
      <el-tabs type="border-card" v-model="currentTabPane">
        <el-tab-pane lazy label="字段选项集" name="option">
          <suspense>
            <option-set v-if="currentTabPane === 'option'" />
            <template #fallback>
              <el-skeleton :rows="3" animated />
            </template>
          </suspense>
        </el-tab-pane>
        <el-tab-pane lazy label="签收环节" :name="ProcessTypeEnum.SIGN">
          <suspense>
            <sign v-if="currentTabPane === ProcessTypeEnum.SIGN" />
            <template #fallback>
              <el-skeleton :rows="3" animated />
            </template>
          </suspense>
        </el-tab-pane>
        <el-tab-pane lazy label="初审环节" :name="ProcessTypeEnum.PRECHECK">
          <suspense>
            <precheck v-if="currentTabPane === ProcessTypeEnum.PRECHECK" />
            <template #fallback>
              <el-skeleton :rows="3" animated />
            </template>
          </suspense>
        </el-tab-pane>
        <el-tab-pane lazy label="录入环节" :name="ProcessTypeEnum.ENTRY">
          <suspense>
            <entry v-if="currentTabPane === ProcessTypeEnum.ENTRY" />
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
            />
            <template #fallback>
              <el-skeleton :rows="3" animated />
            </template>
          </suspense>
        </el-tab-pane>
        <el-tab-pane lazy label="审核环节" :name="ProcessTypeEnum.AUDIT">
          <suspense>
            <audit v-if="currentTabPane === ProcessTypeEnum.AUDIT" />
            <template #fallback>
              <el-skeleton :rows="3" animated />
            </template>
          </suspense>
        </el-tab-pane>
        <el-tab-pane lazy label="复核环节" :name="ProcessTypeEnum.REVIEW">
          <suspense>
            <review v-if="currentTabPane === ProcessTypeEnum.REVIEW" />
            <template #fallback>
              <el-skeleton :rows="3" animated />
            </template>
          </suspense>
        </el-tab-pane>
        <el-tab-pane
          lazy
          label="作业管理环节"
          :name="ProcessTypeEnum.JOB_MANAGE"
        >
          <suspense>
            <job-manage v-if="currentTabPane === ProcessTypeEnum.JOB_MANAGE" />
            <template #fallback>
              <el-skeleton :rows="3" animated />
            </template>
          </suspense>
        </el-tab-pane>
        <el-tab-pane lazy label="导入配置" name="upload">
          <suspense>
            <upload v-if="currentTabPane === 'upload'" />
            <template #fallback>
              <el-skeleton :rows="3" animated />
            </template>
          </suspense>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <publish-dialog
      v-model="publishDialog.visible"
      @close="publishDialog.onClose"
    />
  </div>
</template>

<style lang="scss" scoped></style>
